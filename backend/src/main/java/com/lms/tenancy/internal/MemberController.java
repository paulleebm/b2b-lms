package com.lms.tenancy.internal;

import com.lms.tenancy.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Company admins manage people: who is in, with which role, in which department. */
@RestController
@PreAuthorize("hasRole('ORG_ADMIN')")
class MemberController {

    private final MemberService members;

    MemberController(MemberService members) {
        this.members = members;
    }

    @GetMapping("/v1/members")
    List<MemberView> list() {
        return members.list();
    }

    /**
     * Partial update; absent fields stay as they are. {@code status} accepts ACTIVE or DISABLED.
     * {@code unassignDepartment: true} clears the department (null alone means "unchanged").
     */
    @PatchMapping("/v1/members/{id}")
    MemberView update(@PathVariable UUID id, @RequestBody UpdateMemberRequest request) {
        if (request.status() == Membership.Status.INVITED) {
            throw com.lms.shared.web.ApiException.badRequest("INVALID_STATUS", "상태는 ACTIVE 또는 DISABLED만 지정할 수 있습니다.");
        }
        return members.update(id, new MemberService.MemberChange(
                request.role(), request.departmentId(), Boolean.TRUE.equals(request.unassignDepartment()),
                request.status()));
    }

    /** Cancels a pending invitation. Joined members are disabled through PATCH instead. */
    @DeleteMapping("/v1/members/{id}")
    ResponseEntity<Void> cancelInvitation(@PathVariable UUID id) {
        members.cancelInvitation(id);
        return ResponseEntity.noContent().build();
    }

    /** Invites up to 50 addresses at once; the response reports each address separately. */
    @PostMapping("/v1/invitations")
    InviteResponse invite(@Valid @RequestBody InviteRequest request) {
        return new InviteResponse(members.invite(request.emails(), request.role(), request.departmentId()));
    }

    record UpdateMemberRequest(Role role, UUID departmentId, Boolean unassignDepartment, Membership.Status status) {
    }

    record InviteRequest(
            @NotEmpty @Size(max = 50) List<@NotBlank @Email @Size(max = 254) String> emails,
            @NotNull Role role,
            UUID departmentId) {
    }

    record InviteResponse(List<MemberService.InviteResult> results) {
    }
}
