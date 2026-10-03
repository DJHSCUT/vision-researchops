package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.CreateArtifactRequest;
import com.djh.researchops.dto.UpdateArtifactRequest;
import com.djh.researchops.service.ResultArtifactService;
import com.djh.researchops.vo.ResultArtifactVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResultArtifactController {

    private final ResultArtifactService resultArtifactService;

    @PostMapping("/runs/{runId}/artifacts")
    public ResponseEntity<ApiResponse<ResultArtifactVO>> createArtifact(
            @PathVariable Long runId, @RequestBody CreateArtifactRequest request) {

        // Service 先检查 Run，再统一执行参数校验。
        ResultArtifactVO artifact = resultArtifactService.create(runId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "实验产物创建成功", artifact));
    }

    @GetMapping("/runs/{runId}/artifacts")
    public ApiResponse<List<ResultArtifactVO>> getRunArtifacts(
            @PathVariable Long runId, @RequestParam(required = false) String type) {

        return ApiResponse.success(resultArtifactService.getByRunId(runId, type));
    }

    @GetMapping("/artifacts/{artifactId}")
    public ApiResponse<ResultArtifactVO> getArtifact(@PathVariable Long artifactId) {

        return ApiResponse.success(resultArtifactService.getById(artifactId));
    }

    @PatchMapping("/artifacts/{artifactId}")
    public ApiResponse<ResultArtifactVO> updateArtifact(
            @PathVariable Long artifactId, @RequestBody UpdateArtifactRequest request) {

        return ApiResponse.success(resultArtifactService.update(artifactId, request));
    }

    @DeleteMapping("/artifacts/{artifactId}")
    public ResponseEntity<Void> deleteArtifact(@PathVariable Long artifactId) {

        resultArtifactService.delete(artifactId);
        return ResponseEntity.noContent().build();
    }
}
