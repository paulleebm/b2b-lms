package com.lms.tenancy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only view of the current company's members for other modules. */
public interface MemberDirectory {

    /** membership id → name (or email when no name is set). Unknown ids are absent. */
    Map<UUID, String> displayNames(Collection<UUID> membershipIds);

    /** ACTIVE members currently in any of these departments (membership ids). */
    List<UUID> activeMembersOf(Collection<UUID> departmentIds);

    /** The subset of these memberships that is ACTIVE. Unknown ids are absent. */
    List<UUID> activeAmong(Collection<UUID> membershipIds);

    /** Status of each known membership (ACTIVE, INVITED, DISABLED). */
    Map<UUID, String> statuses(Collection<UUID> membershipIds);

    /** The member's department, if any. */
    Optional<UUID> departmentOf(UUID membershipId);

    /** Who to mail: email and display name of ACTIVE members (others are absent). */
    Map<UUID, Contact> activeContacts(Collection<UUID> membershipIds);

    record Contact(String email, String name) {
    }
}
