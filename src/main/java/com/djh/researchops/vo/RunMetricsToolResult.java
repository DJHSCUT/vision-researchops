package com.djh.researchops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RunMetricsToolResult {

    private Long runId;
    private boolean success;
    private String message;
    private List<ExperimentMetricVO> metrics;
}
