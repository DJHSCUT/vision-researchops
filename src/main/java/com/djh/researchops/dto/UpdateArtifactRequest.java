package com.djh.researchops.dto;

import lombok.Data;

@Data
public class UpdateArtifactRequest {

    private String artifactName;

    private String artifactType;

    private String storagePath;

    private String description;

    private Long fileSizeBytes;
}
