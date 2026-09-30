package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.CreateProjectRequest;
import com.djh.researchops.service.ResearchProjectService;
import com.djh.researchops.vo.ResearchProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}