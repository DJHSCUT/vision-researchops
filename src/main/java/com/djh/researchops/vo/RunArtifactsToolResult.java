package com.djh.researchops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RunArtifactsToolResult {

    private String runCode;
    private String type;
    private boolean success;
    private String message;
    private List<ArtifactToolItem> artifacts;
}
