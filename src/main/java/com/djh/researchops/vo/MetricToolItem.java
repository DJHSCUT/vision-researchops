package com.djh.researchops.vo;

import java.time.LocalDateTime;

public record MetricToolItem(String metricName, Double metricValue, String unit, Long step, LocalDateTime createdAt) {
    public static MetricToolItem from(ExperimentMetricVO metric) {
        return new MetricToolItem(metric.getMetricName(), metric.getMetricValue(), metric.getUnit(),
                metric.getStep(), metric.getCreatedAt());
    }
}
