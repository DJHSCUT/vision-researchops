package com.djh.researchops.vo;

import java.time.LocalDateTime;

/** 供模型使用的任务摘要，仅包含业务编号和业务字段。 */
public record TaskToolItem(String taskCode, String taskName, String status,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static TaskToolItem from(ExperimentTaskVO task) {
        return new TaskToolItem(task.getTaskCode(), task.getName(), task.getStatus(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
