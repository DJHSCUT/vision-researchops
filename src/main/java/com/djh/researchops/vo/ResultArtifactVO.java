package com.djh.researchops.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResultArtifactVO {

    private Long id;

    private Long runId;

    private String artifactName;

    private String artifactType;

    private String storagePath;

    private String description;

    private Long fileSizeBytes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
