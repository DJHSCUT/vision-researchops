package com.djh.researchops.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentRunVO {

    private Long id;

    private String runCode;

    private Long taskId;

    private String runName;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
