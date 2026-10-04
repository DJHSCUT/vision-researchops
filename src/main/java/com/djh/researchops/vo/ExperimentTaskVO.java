package com.djh.researchops.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentTaskVO {

    private Long id;

    private String taskCode;

    private Long projectId;

    private String name;

    private String description;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
