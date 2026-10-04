package com.djh.researchops.vo;

import java.time.LocalDateTime;

public record ArtifactToolItem(String artifactName, String artifactType, String storagePath,
                               String description, Long fileSizeBytes, LocalDateTime createdAt) {
    public static ArtifactToolItem from(ResultArtifactVO artifact) {
        return new ArtifactToolItem(artifact.getArtifactName(), artifact.getArtifactType(), artifact.getStoragePath(),
                artifact.getDescription(), artifact.getFileSizeBytes(), artifact.getCreatedAt());
    }
}
