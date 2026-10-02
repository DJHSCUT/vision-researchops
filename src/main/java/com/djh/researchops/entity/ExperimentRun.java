package com.djh.researchops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment_run")
public class ExperimentRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String runName;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
