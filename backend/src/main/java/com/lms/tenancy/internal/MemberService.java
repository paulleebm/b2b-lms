package com.lms.tenancy.internal;

import com.lms.shared.web.ApiNullable;
import com.lms.organization.DepartmentDirectory;
import com.lms.shared.TenantContext;
import com.lms.shared.web.ApiException;
import com.lms.tenancy.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin operations on the current company's members: list, change role/department,
 * disable/enable, invite, cancel an invitation.
 */
@Service
class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final DepartmentDirectory departments;
    private final KeycloakAdminClient keycloak;
    private final KeycloakProperties keycloakProps;
    private final TransactionTemplate tx;

    MemberService(MembershipRepository memberships, AppUserRepository users, TenantRepository tenants,
                  DepartmentDirectory departments, KeycloakAdminClient keycloak, KeycloakProperties keycloakProps,
                  PlatformTransactionManager txManager) {
        this.memberships = memberships;
        this.users = users;
        this.tenants = tenants;
        this.departments = departments;
        this.keycloak = keycloak;
        this.keycloakProps = keycloakProps;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    List<MemberView> list() {
        return memberships.findAllViews();
    }

    record MemberChange(Role role, UUID departmentId, boolean clearDepartment, Membership.Status status) {
    }

    @Transactional
    MemberView update(UUID membershipId, MemberChange change) {
        Membership m = memberships.findById(membershipId)
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "멤버를 찾을 수 없습니다."));

        boolean losesAdmin = m.getRole() == Role.ORG_ADMIN && m.getStatus() == Membership.Status.ACTIVE
                && ((change.role() != null && change.role() != Role.ORG_ADMIN)
                    || change.status() == Membership.Status.DISABLED);
        if (losesAdmin) {
            if (membershipId.equals(TenantContext.membershipId().orElse(null))) {
                throw ApiException.conflict("CANNOT_DEMOTE_SELF", "자기 자신의 관리자 권한은 바꿀 수 없습니다. 다른 관리자에게 요청하세요.");
            }
            // Lock all active admins, then count: two admins demoting each other at the same time
            // cannot both succeed and leave the company without an admin.
            if (memberships.lockActiveByRole(Role.ORG_ADMIN).size() <= 1) {
                throw ApiException.conflict("LAST_ADMIN", "회사에는 관리자가 최소 한 명 있어야 합니다.");
            }
        }

        if (change.role() != null) {
            m.changeRole(change.role());
        }
        if (change.clearDepartment()) {
            m.assignDepartment(null);
        } else if (change.departmentId() != null) {
            requireDepartment(change.departmentId());
            m.assignDepartment(change.departmentId());
        }
        if (change.status() == Membership.Status.DISABLED) {
            m.disable();
        } else if (change.status() == Membership.Status.ACTIVE) {
            m.enable();
        }
        memberships.flush();
        return view(m);
    }

    // --- invitations ----------------------------------------------------------------------

    enum InviteOutcome { INVITED, ALREADY_MEMBER, FAILED }

    record InviteResult(String email, InviteOutcome outcome, @ApiNullable String message) {
    }

    /**
     * Each address is handled on its own so one failure does not sink the batch.
     * Per address, saga-style:
     * <ol>
     *   <li>Keycloak: find or create the account, add it to the company's organization;</li>
     *   <li>DB: the INVITED membership (one local transaction);</li>
     *   <li>Keycloak: the "set your password" email, only for accounts without a password.</li>
     * </ol>
     * If step 2 fails, step 1's organization membership is removed again (compensation) — but only
     * if this call added it. Step 3 failing does not undo anything: the invitation is real, and
     * inviting the same address again re-sends the email.
     */
    List<InviteResult> invite(List<String> emails, Role role, UUID departmentId) {
        if (departmentId != null) {
            tx.executeWithoutResult(s -> requireDepartment(departmentId));
        }
        UUID invitedBy = MembershipTenantResolver.requireUserId(); // invited_by references app_user
        Tenant tenant = tenants.findById(TenantContext.requireTenantId()).orElseThrow();

        List<InviteResult> results = new ArrayList<>();
        for (String raw : emails.stream().map(e -> e.strip().toLowerCase(Locale.ROOT)).distinct().toList()) {
            results.add(inviteOne(raw, role, departmentId, invitedBy, tenant));
        }
        return results;
    }

    private enum Recorded { NEW, REINVITED, STILL_PENDING, ALREADY_ACTIVE }

    private InviteResult inviteOne(String email, Role role, UUID departmentId, UUID invitedBy, Tenant tenant) {
        KeycloakAdminClient.KeycloakUser kcUser;
        boolean addedToOrganization;
        try {
            kcUser = keycloak.ensureUser(email);
            addedToOrganization = keycloak.addMemberIfAbsent(tenant.getKeycloakOrgId(), kcUser.id());
        } catch (RuntimeException e) {
            log.warn("Invitation to {} failed at the identity provider", email, e);
            return new InviteResult(email, InviteOutcome.FAILED, "인증 서버에 계정을 만들지 못했습니다.");
        }

        Recorded recorded;
        try {
            recorded = tx.execute(s -> {
                AppUser user = users.findByKeycloakSub(kcUser.id())
                        .orElseGet(() -> users.save(new AppUser(kcUser.id(), email, null)));
                Membership existing = memberships.findByUserId(user.getId()).orElse(null);
                if (existing == null) {
                    memberships.save(Membership.invited(user.getId(), role, departmentId, invitedBy));
                    return Recorded.NEW;
                }
                return switch (existing.getStatus()) {
                    case DISABLED -> {
                        existing.reinvite(role, departmentId, invitedBy);
                        yield Recorded.REINVITED;
                    }
                    case INVITED -> Recorded.STILL_PENDING;
                    case ACTIVE -> Recorded.ALREADY_ACTIVE;
                };
            });
        } catch (RuntimeException e) {
            log.warn("Invitation to {} failed after Keycloak; compensating", email, e);
            if (addedToOrganization) {
                try {
                    keycloak.removeMember(tenant.getKeycloakOrgId(), kcUser.id());
                } catch (RuntimeException compensation) {
                    log.error("Compensation failed: {} stays in organization {}", kcUser.id(), tenant.getKeycloakOrgId(), compensation);
                }
            }
            return new InviteResult(email, InviteOutcome.FAILED, "초대를 저장하지 못했습니다. 다시 시도해 주세요.");
        }

        if (recorded == Recorded.ALREADY_ACTIVE) {
            return new InviteResult(email, InviteOutcome.ALREADY_MEMBER, "이미 이 회사의 멤버입니다.");
        }
        if (!kcUser.needsPasswordSetup()) {
            // Already has an account (e.g. from another company): they just sign in, and the
            // membership turns ACTIVE on their first request with this company selected.
            return new InviteResult(email, InviteOutcome.INVITED, null);
        }
        try {
            // After setting the password Keycloak sends them to /auth/login, which signs them in
            // through the BFF; the first API request activates the membership.
            keycloak.sendSetupEmail(kcUser.id(), keycloakProps.webClientId(), keycloakProps.appUrl() + "/auth/login");
        } catch (RuntimeException e) {
            log.warn("Invitation to {} saved, but the setup email failed", email, e);
            return new InviteResult(email, InviteOutcome.INVITED,
                    "초대는 저장했지만 안내 메일을 보내지 못했습니다. 같은 주소로 다시 초대하면 메일을 다시 보냅니다.");
        }
        return new InviteResult(email, InviteOutcome.INVITED,
                recorded == Recorded.STILL_PENDING ? "안내 메일을 다시 보냈습니다." : null);
    }

    /** Only a pending invitation can be cancelled; a joined member is disabled instead. */
    void cancelInvitation(UUID membershipId) {
        record Pending(String keycloakSub, String orgId) {
        }
        Pending pending = tx.execute(s -> {
            Membership m = memberships.findById(membershipId)
                    .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "멤버를 찾을 수 없습니다."));
            if (m.getStatus() != Membership.Status.INVITED) {
                throw ApiException.conflict("NOT_AN_INVITATION", "가입한 멤버는 초대를 취소할 수 없습니다. 비활성화하세요.");
            }
            String sub = users.findById(m.getUserId()).orElseThrow().getKeycloakSub();
            String orgId = tenants.findById(m.getTenantId()).orElseThrow().getKeycloakOrgId();
            memberships.delete(m);
            return new Pending(sub, orgId);
        });
        keycloak.removeMember(pending.orgId(), pending.keycloakSub());
    }

    private void requireDepartment(UUID departmentId) {
        // RLS-scoped lookup; the tenant-scoped foreign key (V2) is the second wall.
        if (!departments.exists(departmentId)) {
            throw ApiException.notFound("DEPARTMENT_NOT_FOUND", "부서를 찾을 수 없습니다.");
        }
    }

    private MemberView view(Membership m) {
        AppUser u = users.findById(m.getUserId()).orElseThrow();
        return new MemberView(m.getId(), m.getUserId(), u.getEmail(), u.getName(), m.getRole(), m.getStatus(),
                m.getDepartmentId(), m.getInvitedAt(), m.getJoinedAt());
    }
}
