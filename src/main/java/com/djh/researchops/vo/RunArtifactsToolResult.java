package com.djh.researchops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RunArtifactsToolResult {

    private Long runId;
    private String type;
    private boolean success;
    private String message;
    private List<ResultArtifactVO> artifacts;
}
