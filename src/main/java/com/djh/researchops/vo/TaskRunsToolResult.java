package com.djh.researchops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskRunsToolResult {

    private Long taskId;
    private String status;
    private boolean success;
    private String message;
    private List<ExperimentRunVO> runs;
}
