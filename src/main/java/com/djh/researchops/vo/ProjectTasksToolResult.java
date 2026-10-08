package com.djh.researchops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ProjectTasksToolResult {
    private String projectCode;
    private String status;
    private boolean success;
    private String message;
    private List<TaskToolItem> tasks;
}
