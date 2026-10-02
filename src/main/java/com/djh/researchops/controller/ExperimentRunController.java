package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.CreateRunRequest;
import com.djh.researchops.dto.UpdateRunRequest;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.vo.ExperimentRunVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExperimentRunController {

    private final ExperimentRunService experimentRunService;

    @PostMapping("/tasks/{taskId}/runs")
    public ResponseEntity<ApiResponse<ExperimentRunVO>> createRun(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateRunRequest request) {

        ExperimentRunVO run = experimentRunService.create(taskId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        201,
                        "实验运行创建成功",
                        run
                ));
    }

    @GetMapping("/tasks/{taskId}/runs")
    public ApiResponse<List<ExperimentRunVO>> getTaskRuns(@PathVariable Long taskId) {

        return ApiResponse.success(
                experimentRunService.getByTaskId(taskId)
        );
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<ExperimentRunVO> getRun(@PathVariable Long id) {

        return ApiResponse.success(
                experimentRunService.getById(id)
        );
    }

    @PatchMapping("/runs/{id}")
    public ApiResponse<ExperimentRunVO> updateRun(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRunRequest request) {

        return ApiResponse.success(
                experimentRunService.update(id, request)
        );
    }

    @DeleteMapping("/runs/{id}")
    public ResponseEntity<Void> deleteRun(@PathVariable Long id) {

        experimentRunService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
