package com.djh.researchops.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTaskRequest {

    @Pattern(regexp = "(?s).*\\S.*", message = "实验任务名称不能为空")
    @Size(max = 150, message = "实验任务名称不能超过150个字符")
    private String name;

    @Size(max = 500, message = "实验任务描述不能超过500个字符")
    private String description;

    private String status;
}
