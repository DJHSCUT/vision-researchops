package com.djh.researchops.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRunRequest {

    @Pattern(regexp = "(?s).*\\S.*", message = "实验运行名称不能为空")
    @Size(max = 150, message = "实验运行名称不能超过150个字符")
    private String runName;

    private String status;

    @Size(max = 1000, message = "实验运行错误信息不能超过1000个字符")
    private String errorMessage;
}
