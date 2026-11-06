package com.lms.catalog.internal;

import com.lms.shared.persistence.AbstractEntity;
import com.lms.shared.web.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * The aggregate root of a course. Sections and lessons are separate rows, but every change to
 * them goes through here ({@link #touch()}), so the course's {@code version} — exposed as the
 * ETag — changes whenever anything in the course changes.
 *
 * <p>DRAFT → PUBLISHED → ARCHIVED. A published course keeps its outline fixed: unpublish to
 * restructure (Day 3 blocks that once people are enrolled).
 */
@Entity
@Table(name = "course")
class Course extends AbstractEntity {

    enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    /** Optimistic lock. Hibernate adds "where version = ?" to every update and bumps it. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {
    }

    Course(String title, String description, UUID ownerId) {
        this.title = title;
        this.description = description == null ? "" : description;
        this.status = Status.DRAFT;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Lost-update guard: the client says which version it edited; anything else is stale. */
    void expectVersion(long expected) {
        if (expected != version) {
            throw ApiException.preconditionFailed("COURSE_CHANGED",
                    "다른 사람이 먼저 이 코스를 수정했습니다. 최신 내용을 불러온 뒤 다시 시도하세요.");
        }
    }

    void touch() {
        this.updatedAt = Instant.now();
    }

    void rename(String title, String description) {
        requireNot(Status.ARCHIVED);
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        touch();
    }

    /** Sections and lessons may change only while the course is a draft. */
    void requireOutlineEditable() {
        if (status != Status.DRAFT) {
            throw ApiException.conflict("COURSE_NOT_EDITABLE", status == Status.PUBLISHED
                    ? "게시된 코스의 목차는 바꿀 수 없습니다. 게시를 취소한 뒤 수정하세요."
                    : "보관된 코스는 수정할 수 없습니다.");
        }
    }

    void publish() {
        requireOutlineEditable();
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
        touch();
    }

    void unpublish() {
        if (status != Status.PUBLISHED) {
            throw ApiException.conflict("COURSE_NOT_PUBLISHED", "게시된 코스만 게시를 취소할 수 있습니다.");
        }
        this.status = Status.DRAFT;
        touch();
    }

    void archive() {
        if (status != Status.PUBLISHED) {
            throw ApiException.conflict("COURSE_NOT_PUBLISHED", "게시된 코스만 보관할 수 있습니다. 초안은 삭제하세요.");
        }
        this.status = Status.ARCHIVED;
        touch();
    }

    private void requireNot(Status forbidden) {
        if (status == forbidden) {
            throw ApiException.conflict("COURSE_NOT_EDITABLE", "보관된 코스는 수정할 수 없습니다.");
        }
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    Status getStatus() {
        return status;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    long getVersion() {
        return version;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
