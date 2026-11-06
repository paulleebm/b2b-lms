package com.lms.tenancy.internal;

import com.lms.tenancy.MemberDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MemberDirectoryService implements MemberDirectory {

    private final MembershipRepository memberships;

    MemberDirectoryService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Collection<UUID> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        return memberships.findDisplayNames(membershipIds).stream()
                .collect(Collectors.toMap(MembershipRepository.DisplayNameRow::getId,
                        MembershipRepository.DisplayNameRow::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeMembersOf(Collection<UUID> departmentIds) {
        return departmentIds.isEmpty() ? List.of() : memberships.findActiveIdsInDepartments(departmentIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeAmong(Collection<UUID> membershipIds) {
        return membershipIds.isEmpty() ? List.of() : memberships.findActiveIdsAmong(membershipIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> statuses(Collection<UUID> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        return memberships.findAllById(membershipIds).stream()
                .collect(Collectors.toMap(Membership::getId, m -> m.getStatus().name()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Contact> activeContacts(Collection<UUID> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        return memberships.findActiveContacts(membershipIds).stream()
                .collect(Collectors.toMap(MembershipRepository.ContactRow::getId,
                        r -> new Contact(r.getEmail(), r.getName() == null ? r.getEmail() : r.getName())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> departmentOf(UUID membershipId) {
        return memberships.findById(membershipId).map(Membership::getDepartmentId);
    }
}
