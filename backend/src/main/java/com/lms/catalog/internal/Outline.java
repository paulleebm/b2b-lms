package com.lms.catalog.internal;

import com.lms.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** A chapter of a course. Position is 0-based and gap-free within the course. */
@Entity
@Table(name = "course_section")
class CourseSection extends AbstractEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CourseSection() {
    }

    CourseSection(UUID courseId, String title, int position) {
        this.courseId = courseId;
        this.title = title;
        this.position = position;
        this.createdAt = Instant.now();
    }

    void rename(String title) {
        this.title = title;
    }

    void moveTo(int position) {
        this.position = position;
    }

    UUID getCourseId() {
        return courseId;
    }

    String getTitle() {
        return title;
    }

    int getPosition() {
        return position;
    }
}

/** One unit of learning: a video, a document, or a text page. */
@Entity
@Table(name = "lesson")
class Lesson extends AbstractEntity {

    enum Type { VIDEO, DOCUMENT, TEXT }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Type type;

    @Column(nullable = false)
    private int position;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "video_asset_id")
    private UUID videoAssetId;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lesson() {
    }

    Lesson(UUID courseId, UUID sectionId, String title, Type type, int position) {
        this.courseId = courseId;
        this.sectionId = sectionId;
        this.title = title;
        this.type = type;
        this.position = position;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void rename(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    void writeBody(String body) {
        this.body = body;
        this.updatedAt = Instant.now();
    }

    void attachVideo(UUID videoAssetId) {
        this.videoAssetId = videoAssetId;
        this.updatedAt = Instant.now();
    }

    void attachDocument(UUID fileId) {
        this.fileId = fileId;
        this.updatedAt = Instant.now();
    }

    void moveTo(UUID sectionId, int position) {
        this.sectionId = sectionId;
        this.position = position;
    }

    UUID getCourseId() {
        return courseId;
    }

    UUID getSectionId() {
        return sectionId;
    }

    String getTitle() {
        return title;
    }

    Type getType() {
        return type;
    }

    int getPosition() {
        return position;
    }

    String getBody() {
        return body;
    }

    UUID getVideoAssetId() {
        return videoAssetId;
    }

    UUID getFileId() {
        return fileId;
    }
}
