package com.lms.tenancy.internal;

import com.lms.tenancy.MemberDirectory;
import java.util.Collection;
import java.util.Map;
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
}
