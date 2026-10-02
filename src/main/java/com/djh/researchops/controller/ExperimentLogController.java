package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.CreateLogRequest;
import com.djh.researchops.service.ExperimentLogService;
import com.djh.researchops.vo.ExperimentLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/runs/{runId}/logs")
@RequiredArgsConstructor
public class ExperimentLogController {

    private final ExperimentLogService experimentLogService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExperimentLogVO>> createLog(
            @PathVariable Long runId,
            @Valid @RequestBody CreateLogRequest request) {

        ExperimentLogVO log = experimentLogService.create(runId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        201,
                        "实验日志创建成功",
                        log
                ));
    }

    @GetMapping
    public ApiResponse<List<ExperimentLogVO>> getRunLogs(
            @PathVariable Long runId,
            @RequestParam(required = false) String level) {

        return ApiResponse.success(
                experimentLogService.getByRunId(runId, level)
        );
    }
}
