package com.djh.researchops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateMetricRequest {

    @NotBlank(message = "实验指标名称不能为空")
    @Size(max = 100, message = "实验指标名称不能超过100个字符")
    private String metricName;

    @NotNull(message = "实验指标数值不能为空")
    private Double metricValue;

    @Size(max = 50, message = "实验指标单位不能超过50个字符")
    private String unit;

    @PositiveOrZero(message = "实验指标步数不能小于0")
    private Long step;
}
