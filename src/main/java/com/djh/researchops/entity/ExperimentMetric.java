package com.djh.researchops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment_metric")
public class ExperimentMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String metricName;

    private Double metricValue;

    private String unit;

    private Long step;

    private LocalDateTime createdAt;
}
