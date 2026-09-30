package com.djh.researchops.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectRequest {

    @Pattern(regexp = "(?s).*\\S.*", message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "项目描述不能超过500个字符")
    private String description;

    private String status;
}
