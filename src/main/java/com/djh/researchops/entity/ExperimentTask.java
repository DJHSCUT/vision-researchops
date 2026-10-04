package com.djh.researchops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment_task")
public class ExperimentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String taskCode;

    private Long projectId;

    private String name;

    private String description;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
