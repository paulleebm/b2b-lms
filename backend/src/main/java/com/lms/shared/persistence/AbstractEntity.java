package com.lms.shared.persistence;

import com.lms.shared.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Base for entities with an application-assigned UUID v7.
 *
 * <p>Implements {@link Persistable} so Spring Data knows a freshly constructed entity is new.
 * Otherwise {@code save()} sees a non-null id, assumes the row exists, and runs a SELECT before
 * every INSERT ({@code merge} instead of {@code persist}).
 */
@MappedSuperclass
public abstract class AbstractEntity implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected AbstractEntity() {
        this.id = UuidV7.generate();
    }

    protected AbstractEntity(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
