package com.lms.files.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    List<StoredFile> findAllByIdIn(Collection<UUID> ids);
}

interface VideoAssetRepository extends JpaRepository<VideoAsset, UUID> {

    List<VideoAsset> findAllByIdIn(Collection<UUID> ids);

    Optional<VideoAsset> findBySourceFileId(UUID sourceFileId);
}
