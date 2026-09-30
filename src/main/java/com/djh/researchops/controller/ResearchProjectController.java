package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.CreateProjectRequest;
import com.djh.researchops.dto.UpdateProjectRequest;
import com.djh.researchops.service.ResearchProjectService;
import com.djh.researchops.vo.ResearchProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ResearchProjectController {

    private final ResearchProjectService researchProjectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ResearchProjectVO>> createProject(
            @Valid @RequestBody CreateProjectRequest request) {

        ResearchProjectVO project =
                researchProjectService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        201,
                        "项目创建成功",
                        project
                ));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResearchProjectVO> getProject(
            @PathVariable Long id) {

        return ApiResponse.success(
                researchProjectService.getById(id)
        );
    }

    @GetMapping
    public ApiResponse<List<ResearchProjectVO>> getProjects() {

        return ApiResponse.success(
                researchProjectService.getAll()
        );
    }

    @PatchMapping("/{id}")
    public ApiResponse<ResearchProjectVO> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request) {

        return ApiResponse.success(
                researchProjectService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {

        researchProjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
