package com.djh.researchops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateArtifactRequest {

    @NotBlank(message = "实验产物名称不能为空")
    @Size(max = 200, message = "实验产物名称不能超过200个字符")
    private String artifactName;

    @NotBlank(message = "实验产物类型不合法")
    private String artifactType;

    @NotBlank(message = "实验产物路径不能为空")
    @Size(max = 1000, message = "实验产物路径不能超过1000个字符")
    private String storagePath;

    @Size(max = 500, message = "实验产物描述不能超过500个字符")
    private String description;

    @PositiveOrZero(message = "实验产物文件大小不能小于0")
    private Long fileSizeBytes;
}
