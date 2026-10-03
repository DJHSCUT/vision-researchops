package com.djh.researchops.controller;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.dto.AiChatRequest;
import com.djh.researchops.service.AiChatService;
import com.djh.researchops.vo.AiChatVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Profile("ai")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ApiResponse<AiChatVO> chat(@Valid @RequestBody AiChatRequest request) {

        return ApiResponse.success(aiChatService.chat(request.getMessage()));
    }
}
