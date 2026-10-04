package com.djh.researchops.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RunLogsToolResult {

    private String runCode;
    private String level;
    private boolean success;
    private String message;
    private List<LogToolItem> logs;
}
