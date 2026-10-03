package com.djh.researchops.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMetricRequest {

    private String metricName;

    private Double metricValue;

    private String unit;

    private Long step;

    @JsonIgnore
    @Setter(lombok.AccessLevel.NONE)
    private boolean stepProvided;

    // Jackson 仅在请求包含 step 时调用，显式 null 表示清空。
    public void setStep(Long step) {
        this.step = step;
        this.stepProvided = true;
    }
}
