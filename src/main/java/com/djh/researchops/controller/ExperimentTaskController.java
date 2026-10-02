package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.CreateTaskRequest;
import com.djh.researchops.dto.UpdateTaskRequest;
import com.djh.researchops.service.ExperimentTaskService;
import com.djh.researchops.vo.ExperimentTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExperimentTaskController {

    private final ExperimentTaskService experimentTaskService;

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<ApiResponse<ExperimentTaskVO>> createTask(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request) {

        ExperimentTaskVO task = experimentTaskService.create(projectId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        201,
                        "实验任务创建成功",
                        task
                ));
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ApiResponse<List<ExperimentTaskVO>> getProjectTasks(
            @PathVariable Long projectId) {

        return ApiResponse.success(
                experimentTaskService.getByProjectId(projectId)
        );
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<ExperimentTaskVO> getTask(@PathVariable Long id) {

        return ApiResponse.success(
                experimentTaskService.getById(id)
        );
    }

    @PatchMapping("/tasks/{id}")
    public ApiResponse<ExperimentTaskVO> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request) {

        return ApiResponse.success(
                experimentTaskService.update(id, request)
        );
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {

        experimentTaskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
