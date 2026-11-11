package com.lms.learning.internal;

import com.lms.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ORG_ADMIN')")
class AssignmentController {

    private final AssignmentService service;

    AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/v1/assignments")
    List<AssignmentService.AssignmentView> list() {
        return service.list();
    }

    /**
     * Idempotency-Key (optional, recommended): a double click or a network retry with the same
     * key returns the first result instead of assigning twice. Replays answer 200 with
     * {@code Idempotent-Replayed: true}; first runs answer 201.
     */
    @PostMapping("/v1/assignments")
    ResponseEntity<AssignmentService.AssignResult> assign(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AssignRequest request) {
        if (key != null && !key.matches("^[A-Za-z0-9_-]{8,100}$")) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key는 영문·숫자·-_ 8~100자입니다.");
        }
        var cmd = new AssignmentService.AssignCommand(request.courseId(),
                Optional.ofNullable(request.departmentIds()).orElse(List.of()),
                Optional.ofNullable(request.membershipIds()).orElse(List.of()), request.dueAt());
        if (key != null) {
            Optional<AssignmentService.AssignResult> replay = service.replay(key, hashOf(cmd));
            if (replay.isPresent()) {
                return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(replay.get());
            }
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(cmd, key));
        } catch (DataIntegrityViolationException raceLost) {
            // The same key committed first in a concurrent request; ours rolled back as a whole.
            return service.replay(key, hashOf(cmd))
                    .map(r -> ResponseEntity.ok().header("Idempotent-Replayed", "true").body(r))
                    .orElseThrow(() -> raceLost);
        }
    }

    private static String hashOf(AssignmentService.AssignCommand cmd) {
        return AssignmentService.hash(cmd);
    }

    /** @param dueAt null = no deadline */
    record AssignRequest(
            @NotNull UUID courseId,
            @Size(max = 100) List<@NotNull UUID> departmentIds,
            @Size(max = 500) List<@NotNull UUID> membershipIds,
            Instant dueAt) {
    }
}
