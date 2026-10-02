package com.djh.researchops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRunRequest {

    @NotBlank(message = "实验运行名称不能为空")
    @Size(max = 150, message = "实验运行名称不能超过150个字符")
    private String runName;
}
