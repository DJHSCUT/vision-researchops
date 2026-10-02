package com.djh.researchops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment_log")
public class ExperimentLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String level;

    private String content;

    private LocalDateTime createdAt;
}
