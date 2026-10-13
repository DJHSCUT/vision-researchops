package com.djh.researchops.rag;

import com.djh.researchops.common.ApiResponse;
import com.djh.researchops.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("ai & rag & !prod & !production")
@ConditionalOnProperty(prefix = "researchops.rag", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "researchops.rag.ask", name = "enabled", havingValue = "true")
public class RagAskController {
    private final RagAskService service;
    public RagAskController(RagAskService service) { this.service = service; }

    @PostMapping("/dev/rag/ask")
    public ApiResponse<RagAskResponse> ask(@RequestBody RagAskRequest request, HttpServletRequest servletRequest) {
        String remote = servletRequest.getRemoteAddr();
        if (remote == null || !Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1").contains(remote)
                || servletRequest.getHeader("Forwarded") != null || servletRequest.getHeader("X-Forwarded-For") != null
                || servletRequest.getHeader("X-Real-IP") != null) {
            throw new BusinessException(403, "RAG 问答仅允许可信本机开发环境直接访问");
        }
        return ApiResponse.success(service.ask(request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidJson() {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求 JSON 无效：question 必须为字符串，topK 必须为整数"));
    }
}
