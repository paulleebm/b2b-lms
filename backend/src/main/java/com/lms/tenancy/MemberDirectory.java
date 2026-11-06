package com.lms.tenancy;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only view of the current company's members for other modules. */
public interface MemberDirectory {

    /** membership id → name (or email when no name is set). Unknown ids are absent. */
    Map<UUID, String> displayNames(Collection<UUID> membershipIds);
}
