package com.lms.organization.internal;

import com.lms.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Departments of the current company. Day 1 keeps this small; it is the first tenant-owned
 * resource and the subject of the cross-tenant isolation tests.
 */
@RestController
@RequestMapping("/v1/departments")
@PreAuthorize("hasRole('ORG_ADMIN')")
class DepartmentController {

    private final DepartmentService service;

    DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    List<DepartmentResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    DepartmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse created = service.create(request.name().strip());
        return ResponseEntity.created(URI.create("/v1/departments/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    DepartmentResponse rename(@PathVariable UUID id, @Valid @RequestBody CreateDepartmentRequest request) {
        return service.rename(id, request.name().strip());
    }

    /** 409 DEPARTMENT_IN_USE while members are assigned (the tenant-scoped foreign key decides). */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    record CreateDepartmentRequest(@NotBlank @Size(max = 100) String name) {
    }

    record DepartmentResponse(UUID id, String name, Instant createdAt) {
        static DepartmentResponse of(Department d) {
            return new DepartmentResponse(d.getId(), d.getName(), d.getCreatedAt());
        }
    }
}

@org.springframework.stereotype.Service
class DepartmentService implements com.lms.organization.DepartmentDirectory {

    private final DepartmentRepository departments;

    DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID departmentId) {
        return departments.existsById(departmentId);
    }

    @Transactional
    DepartmentController.DepartmentResponse rename(UUID id, String name) {
        Department department = departments.findById(id)
                .orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "부서를 찾을 수 없습니다."));
        if (!department.getName().equals(name) && departments.existsByName(name)) {
            throw ApiException.conflict("DEPARTMENT_NAME_TAKEN", "같은 이름의 부서가 이미 있습니다.");
        }
        department.rename(name);
        return DepartmentController.DepartmentResponse.of(department);
    }

    @Transactional
    void delete(UUID id) {
        Department department = departments.findById(id)
                .orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "부서를 찾을 수 없습니다."));
        try {
            departments.delete(department);
            departments.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DEPARTMENT_IN_USE", "이 부서에 배치된 멤버가 있습니다. 멤버의 부서를 먼저 바꾸세요.");
        }
    }

    @Transactional(readOnly = true)
    List<DepartmentController.DepartmentResponse> list() {
        return departments.findAllByOrderByNameAsc().stream().map(DepartmentController.DepartmentResponse::of).toList();
    }

    @Transactional(readOnly = true)
    DepartmentController.DepartmentResponse get(UUID id) {
        // Another company's department simply does not exist from here: 404, never 403.
        return departments.findById(id)
                .map(DepartmentController.DepartmentResponse::of)
                .orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "부서를 찾을 수 없습니다."));
    }

    @Transactional
    DepartmentController.DepartmentResponse create(String name) {
        if (departments.existsByName(name)) {
            throw ApiException.conflict("DEPARTMENT_NAME_TAKEN", "같은 이름의 부서가 이미 있습니다.");
        }
        try {
            return DepartmentController.DepartmentResponse.of(departments.saveAndFlush(new Department(name)));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DEPARTMENT_NAME_TAKEN", "같은 이름의 부서가 이미 있습니다.");
        }
    }
}
