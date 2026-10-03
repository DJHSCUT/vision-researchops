package com.djh.researchops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("result_artifact")
public class ResultArtifact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String artifactName;

    private String artifactType;

    private String storagePath;

    private String description;

    private Long fileSizeBytes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
