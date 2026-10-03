package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.CreateMetricRequest;
import com.djh.researchops.dto.UpdateMetricRequest;
import com.djh.researchops.service.ExperimentMetricService;
import com.djh.researchops.vo.ExperimentMetricVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExperimentMetricController {

    private final ExperimentMetricService experimentMetricService;

    @PostMapping("/runs/{runId}/metrics")
    public ResponseEntity<ApiResponse<ExperimentMetricVO>> createMetric(
            @PathVariable Long runId,
            @Valid @RequestBody CreateMetricRequest request) {

        ExperimentMetricVO metric = experimentMetricService.create(runId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "实验指标创建成功", metric));
    }

    @GetMapping("/runs/{runId}/metrics")
    public ApiResponse<List<ExperimentMetricVO>> getRunMetrics(
            @PathVariable Long runId,
            @RequestParam(required = false) String name) {

        return ApiResponse.success(experimentMetricService.getByRunId(runId, name));
    }

    @PatchMapping("/metrics/{metricId}")
    public ApiResponse<ExperimentMetricVO> updateMetric(
            @PathVariable Long metricId,
            @RequestBody UpdateMetricRequest request) {

        return ApiResponse.success(experimentMetricService.update(metricId, request));
    }

    @DeleteMapping("/metrics/{metricId}")
    public ResponseEntity<Void> deleteMetric(@PathVariable Long metricId) {

        experimentMetricService.delete(metricId);
        return ResponseEntity.noContent().build();
    }
}
