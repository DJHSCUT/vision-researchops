package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ResultArtifactService;
import com.djh.researchops.tool.RunArtifactTools;
import com.djh.researchops.vo.ResultArtifactVO;
import com.djh.researchops.vo.RunArtifactsToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 仅模拟业务 Service，不连接 MySQL、访问产物文件或调用外部模型。
class RunArtifactToolsTests {

    private ResultArtifactService service;
    private RunArtifactTools tools;

    @BeforeEach
    void setUp() {
        service = mock(ResultArtifactService.class);
        tools = new RunArtifactTools(service);
    }

    @Test
    void allArtifactsQuerySucceeds() { assertSuccessfulQuery(null, null); }

    @Test
    void imageQuerySucceeds() { assertSuccessfulQuery("IMAGE", "IMAGE"); }

    @Test
    void modelQuerySucceeds() { assertSuccessfulQuery("MODEL", "MODEL"); }

    @Test
    void pointCloudQuerySucceeds() { assertSuccessfulQuery("POINT_CLOUD", "POINT_CLOUD"); }

    @Test
    void checkpointQuerySucceeds() { assertSuccessfulQuery("CHECKPOINT", "CHECKPOINT"); }

    @Test
    void reportQuerySucceeds() { assertSuccessfulQuery("REPORT", "REPORT"); }

    @Test
    void otherQuerySucceeds() { assertSuccessfulQuery("OTHER", "OTHER"); }

    @Test
    void lowercaseModelIsNormalized() { assertSuccessfulQuery("model", "MODEL"); }

    @Test
    void mixedCaseModelIsNormalized() { assertSuccessfulQuery("Model", "MODEL"); }

    @Test
    void surroundingWhitespaceIsTrimmed() { assertSuccessfulQuery(" MODEL ", "MODEL"); }

    @Test
    void lowercasePointCloudIsNormalized() { assertSuccessfulQuery("point_cloud", "POINT_CLOUD"); }

    @Test
    void spaceSeparatedPointCloudIsNormalized() { assertSuccessfulQuery(" point cloud ", "POINT_CLOUD"); }

    @Test
    void invalidVideoReturnsStructuredFailure() { assertInvalidType("VIDEO"); }

    @Test
    void invalidTypeDoesNotCallService() {
        tools.queryRunArtifacts(1L, "VIDEO");
        verifyNoInteractions(service);
    }

    @Test
    void emptyTypeIsInvalid() { assertInvalidType(""); }

    @Test
    void whitespaceTypeIsInvalid() { assertInvalidType("   "); }

    @Test
    void chineseTypeIsNotFuzzyMatched() { assertInvalidType("点云"); }

    @Test
    void nullRunIdReturnsParameterError() { assertInvalidRunId(null); }

    @Test
    void zeroRunIdReturnsParameterError() { assertInvalidRunId(0L); }

    @Test
    void negativeRunIdReturnsParameterError() { assertInvalidRunId(-1L); }

    @Test
    void multipleArtifactsPreserveOriginalListAndOrder() {
        List<ResultArtifactVO> artifacts = List.of(artifact(7L, "MODEL"), artifact(3L, "POINT_CLOUD"));
        when(service.getByRunId(1L, null)).thenReturn(artifacts);
        assertSame(artifacts, tools.queryRunArtifacts(1L, null).getArtifacts());
    }

    @Test
    void storagePathIsPreservedWithoutFileAccess() {
        ResultArtifactVO artifact = artifact(7L, "MODEL");
        artifact.setStoragePath("/test-only/nonexistent/model file.bin");
        when(service.getByRunId(1L, "MODEL")).thenReturn(List.of(artifact));
        assertEquals(artifact.getStoragePath(), tools.queryRunArtifacts(1L, "MODEL").getArtifacts().get(0).getStoragePath());
    }

    @Test
    void fileSizeBytesIsPreservedAsLong() {
        ResultArtifactVO artifact = artifact(7L, "MODEL");
        artifact.setFileSizeBytes(5_000_000_123L);
        when(service.getByRunId(1L, "MODEL")).thenReturn(List.of(artifact));
        assertEquals(5_000_000_123L, tools.queryRunArtifacts(1L, "MODEL").getArtifacts().get(0).getFileSizeBytes());
    }

    @Test
    void existingRunWithoutArtifactsReturnsSuccess() {
        when(service.getByRunId(1L, null)).thenReturn(List.of());
        RunArtifactsToolResult result = tools.queryRunArtifacts(1L, null);
        assertTrue(result.isSuccess());
        assertEquals("当前实验运行暂无实验产物", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
    }

    @Test
    void existingRunWithoutModelArtifactsReturnsSuccess() {
        when(service.getByRunId(1L, "MODEL")).thenReturn(List.of());
        RunArtifactsToolResult result = tools.queryRunArtifacts(1L, "model");
        assertTrue(result.isSuccess());
        assertEquals("MODEL", result.getType());
        assertEquals("当前实验运行暂无 MODEL 类型实验产物", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
    }

    @Test
    void missingRunReturnsStructuredFailure() {
        when(service.getByRunId(99999L, null)).thenThrow(new BusinessException(404, "实验运行不存在"));
        RunArtifactsToolResult result = tools.queryRunArtifacts(99999L, null);
        assertEquals(99999L, result.getRunId());
        assertNull(result.getType());
        assertFalse(result.isSuccess());
        assertEquals("实验运行不存在", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
    }

    @Test
    void unknownSystemFailureIsRethrown() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByRunId(1L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryRunArtifacts(1L, null)));
    }

    @Test
    void unrelatedBusinessFailuresAreRethrown() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "实验产物不存在"))) {
            doThrow(failure).when(service).getByRunId(1L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryRunArtifacts(1L, null)));
        }
    }

    @Test
    void delegatesExactlyOnceToExistingService() {
        when(service.getByRunId(1L, "MODEL")).thenReturn(List.of());
        tools.queryRunArtifacts(1L, "model");
        verify(service).getByRunId(1L, "MODEL");
        verifyNoMoreInteractions(service);
    }

    @Test
    void toolDependsOnlyOnArtifactServiceWithoutMapperDependency() {
        assertArrayEquals(new Class<?>[]{ResultArtifactService.class},
                RunArtifactTools.class.getConstructors()[0].getParameterTypes());
        assertTrue(Arrays.stream(RunArtifactTools.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getPackageName().contains(".mapper")));
    }

    @Test
    void springAiSchemaMakesTypeOptionalAndAcceptsOmittedType() {
        var callback = ToolCallbacks.from(tools)[0];
        assertEquals("queryRunArtifacts", callback.getToolDefinition().name());
        var mapper = JsonMapper.builder().build();
        var schema = mapper.readTree(callback.getToolDefinition().inputSchema());
        assertEquals(List.of("runId"), mapper.convertValue(schema.get("required"), List.class));
        when(service.getByRunId(1L, null)).thenReturn(List.of());
        var result = mapper.readTree(callback.call("{\"runId\":1}"));
        assertTrue(result.get("success").asBoolean());
        assertEquals("当前实验运行暂无实验产物", result.get("message").asString());
        verify(service).getByRunId(1L, null);
    }

    @Test
    void springAiCallbackAcceptsExplicitNullType() {
        when(service.getByRunId(1L, null)).thenReturn(List.of());
        var result = JsonMapper.builder().build().readTree(
                ToolCallbacks.from(tools)[0].call("{\"runId\":1,\"type\":null}"));
        assertTrue(result.get("success").asBoolean());
        assertTrue(result.get("type").isNull());
        verify(service).getByRunId(1L, null);
    }

    private void assertSuccessfulQuery(String input, String expectedType) {
        List<ResultArtifactVO> artifacts = List.of(artifact(7L, expectedType == null ? "MODEL" : expectedType));
        when(service.getByRunId(1L, expectedType)).thenReturn(artifacts);
        RunArtifactsToolResult result = tools.queryRunArtifacts(1L, input);
        assertEquals(1L, result.getRunId());
        assertEquals(expectedType, result.getType());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
        assertSame(artifacts, result.getArtifacts());
        verify(service).getByRunId(1L, expectedType);
    }

    private void assertInvalidType(String type) {
        RunArtifactsToolResult result = tools.queryRunArtifacts(1L, type);
        assertFalse(result.isSuccess());
        assertEquals("实验产物类型不合法", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
        verifyNoInteractions(service);
    }

    private void assertInvalidRunId(Long runId) {
        RunArtifactsToolResult result = tools.queryRunArtifacts(runId, null);
        assertEquals(runId, result.getRunId());
        assertFalse(result.isSuccess());
        assertEquals("实验运行 ID 必须为正整数", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
        verifyNoInteractions(service);
    }

    private ResultArtifactVO artifact(Long id, String type) {
        ResultArtifactVO artifact = new ResultArtifactVO();
        artifact.setId(id);
        artifact.setRunId(1L);
        artifact.setArtifactName("测试产物 " + id);
        artifact.setArtifactType(type);
        artifact.setStoragePath("/test-only/nonexistent/" + id);
        artifact.setDescription("测试元数据");
        artifact.setFileSizeBytes(104857600L);
        artifact.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        artifact.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 5, 6));
        return artifact;
    }
}
