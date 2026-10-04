package com.djh.researchops.vo;

import java.time.LocalDateTime;

public record RunToolItem(String runCode, String runName, String status, LocalDateTime createdAt,
                          LocalDateTime startedAt, LocalDateTime finishedAt, String errorMessage) {
    public static RunToolItem from(ExperimentRunVO run) {
        return new RunToolItem(run.getRunCode(), run.getRunName(), run.getStatus(), run.getCreatedAt(),
                run.getStartedAt(), run.getFinishedAt(), run.getErrorMessage());
    }
}
