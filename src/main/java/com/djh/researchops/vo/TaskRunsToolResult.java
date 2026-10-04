package com.djh.researchops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskRunsToolResult {

    private String taskCode;
    private String status;
    private boolean success;
    private String message;
    private List<RunToolItem> runs;
}
