package com.djh.researchops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("research_project")
public class ResearchProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String projectCode;

    private String name;

    private String description;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
