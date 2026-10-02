package com.djh.researchops.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentLogVO {

    private Long id;

    private Long runId;

    private String level;

    private String content;

    private LocalDateTime createdAt;
}
