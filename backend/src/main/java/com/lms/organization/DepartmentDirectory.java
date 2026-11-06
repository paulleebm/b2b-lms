package com.lms.organization;

import java.util.UUID;

/**
 * Public API of the organization module (Spring Modulith: only base-package types may be used
 * by other modules). Lookups run inside the caller's tenant context, so another company's
 * department simply does not exist here.
 */
public interface DepartmentDirectory {

    boolean exists(UUID departmentId);
}
