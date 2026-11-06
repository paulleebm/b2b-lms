package com.lms.catalog.internal;

import com.lms.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/** The course's final quiz (at most one). Editing bumps {@code revision}; attempts record it. */
@Entity
@Table(name = "quiz")
class Quiz extends AbstractEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "pass_score", nullable = false)
    private int passScore;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "time_limit_sec")
    private Integer timeLimitSec;

    @Column(nullable = false)
    private int revision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Quiz() {
    }

    Quiz(UUID courseId) {
        this.courseId = courseId;
        this.revision = 0;
        this.createdAt = Instant.now();
    }

    void configure(int passScore, Integer maxAttempts, Integer timeLimitSec) {
        this.passScore = passScore;
        this.maxAttempts = maxAttempts;
        this.timeLimitSec = timeLimitSec;
        this.revision++;
    }

    UUID getCourseId() {
        return courseId;
    }

    int getPassScore() {
        return passScore;
    }

    Integer getMaxAttempts() {
        return maxAttempts;
    }

    Integer getTimeLimitSec() {
        return timeLimitSec;
    }

    int getRevision() {
        return revision;
    }
}

/**
 * One question. Options and the answer key are JSON (their shape varies per question);
 * they are stored as JSON text and (de)serialized by {@link QuizJson}.
 */
@Entity
@Table(name = "question")
class Question extends AbstractEntity {

    enum Type { SINGLE, MULTIPLE }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String options;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String answer;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private int position;

    protected Question() {
    }

    Question(UUID quizId, Type type, String prompt, String optionsJson, String answerJson, int points, int position) {
        this.quizId = quizId;
        this.type = type;
        this.prompt = prompt;
        this.options = optionsJson;
        this.answer = answerJson;
        this.points = points;
        this.position = position;
    }

    UUID getQuizId() {
        return quizId;
    }

    Type getType() {
        return type;
    }

    String getPrompt() {
        return prompt;
    }

    String getOptions() {
        return options;
    }

    String getAnswer() {
        return answer;
    }

    int getPoints() {
        return points;
    }

    int getPosition() {
        return position;
    }
}
