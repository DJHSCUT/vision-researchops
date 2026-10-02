package com.djh.researchops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateLogRequest {

    private String level;

    @NotBlank(message = "实验日志内容不能为空")
    @Size(max = 5000, message = "实验日志内容不能超过5000个字符")
    private String content;
}
