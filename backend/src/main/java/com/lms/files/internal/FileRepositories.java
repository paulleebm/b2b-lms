package com.lms.files.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    List<StoredFile> findAllByIdIn(Collection<UUID> ids);

    @org.springframework.data.jpa.repository.Query(
            "select f from StoredFile f where f.status = com.lms.files.internal.StoredFile.Status.PENDING and f.createdAt < :cutoff")
    List<StoredFile> findAbandoned(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}

interface VideoAssetRepository extends JpaRepository<VideoAsset, UUID> {

    List<VideoAsset> findAllByIdIn(Collection<UUID> ids);

    Optional<VideoAsset> findBySourceFileId(UUID sourceFileId);

    @org.springframework.data.jpa.repository.Query(
            "select v from VideoAsset v where v.status = com.lms.files.MediaLibrary.VideoState.PROCESSING and v.createdAt < :cutoff")
    List<VideoAsset> findStuck(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
