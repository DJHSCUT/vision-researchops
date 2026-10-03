package com.djh.researchops.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentMetricVO {

    private Long id;

    private Long runId;

    private String metricName;

    private Double metricValue;

    private String unit;

    private Long step;

    private LocalDateTime createdAt;
}
