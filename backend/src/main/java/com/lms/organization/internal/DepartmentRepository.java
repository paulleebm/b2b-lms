package com.lms.organization.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Note: no "tenantId" in any method — Hibernate @TenantId and RLS scope every query. */
interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}
