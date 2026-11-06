package com.lms.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Company-level facts for other modules (certificates print the name; scheduled jobs visit every company). */
public interface Companies {

    Optional<String> name(UUID tenantId);

    /** Every company id. Global table: used by jobs that then work inside each tenant in turn. */
    List<UUID> allIds();
}
