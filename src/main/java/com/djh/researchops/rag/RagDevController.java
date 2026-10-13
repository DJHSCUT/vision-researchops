package com.djh.researchops.rag;

import com.djh.researchops.common.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit local development endpoints; no ChatModel/AI Tools/business-table dependencies. */
@RestController
@RequestMapping("/dev/rag")
@Profile("rag")
@ConditionalOnProperty(prefix = "researchops.rag", name = "enabled", havingValue = "true")
public class RagDevController {
    private final RagService service;

    public RagDevController(RagService service) { this.service = service; }

    @PostMapping("/demo/import")
    public ApiResponse<RagService.ImportResult> importDemo() { return ApiResponse.success(service.importDemo()); }

    @GetMapping("/search")
    public ApiResponse<List<RagService.SearchHit>> search(@RequestParam String query,
                                                        @RequestParam(defaultValue = "3") int topK) {
        return ApiResponse.success(service.search(query, topK));
    }
}
