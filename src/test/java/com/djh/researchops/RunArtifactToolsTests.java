package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ResultArtifactService;
import com.djh.researchops.tool.RunArtifactTools;
import com.djh.researchops.vo.ResultArtifactVO;
import com.djh.researchops.vo.RunArtifactsToolResult;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.vo.ExperimentRunVO;
import com.djh.researchops.vo.ArtifactToolItem;
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
    private ExperimentRunService resolver;

    @BeforeEach
    void setUp() {
        service = mock(ResultArtifactService.class);
        resolver = mock(ExperimentRunService.class);
        ExperimentRunVO resolved = new ExperimentRunVO();
        resolved.setId(101L);
        resolved.setRunCode("R-1");
        when(resolver.getByRunCode("R-1")).thenReturn(resolved);
        tools = new RunArtifactTools(service, resolver);
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
        tools.queryRunArtifacts("R-1", "VIDEO");
        verifyNoInteractions(service, resolver);
    }

    @Test
    void emptyTypeIsInvalid() { assertInvalidType(""); }

    @Test
    void whitespaceTypeIsInvalid() { assertInvalidType("   "); }

    @Test
    void chineseTypeIsNotFuzzyMatched() { assertInvalidType("点云"); }

    @Test
    void nullRunCodeReturnsParameterError() { assertInvalidRunCode(null); }

    @Test
    void zeroRunCodeReturnsParameterError() { assertInvalidRunCode("R-0"); }

    @Test
    void negativeRunCodeReturnsParameterError() { assertInvalidRunCode("R--1"); }

    @Test
    void multipleArtifactsPreserveFieldsAndServiceOrder() {
        List<ResultArtifactVO> artifacts = List.of(artifact(7L, "MODEL"), artifact(3L, "POINT_CLOUD"));
        when(service.getByRunId(101L, null)).thenReturn(artifacts);
        assertEquals(artifacts.stream().map(ArtifactToolItem::from).toList(), tools.queryRunArtifacts("R-1", null).getArtifacts());
    }

    @Test
    void storagePathIsPreservedWithoutFileAccess() {
        ResultArtifactVO artifact = artifact(7L, "MODEL");
        artifact.setStoragePath("/test-only/nonexistent/model file.bin");
        when(service.getByRunId(101L, "MODEL")).thenReturn(List.of(artifact));
        assertEquals(artifact.getStoragePath(), tools.queryRunArtifacts("R-1", "MODEL").getArtifacts().get(0).storagePath());
    }

    @Test
    void fileSizeBytesIsPreservedAsLong() {
        ResultArtifactVO artifact = artifact(7L, "MODEL");
        artifact.setFileSizeBytes(5_000_000_123L);
        when(service.getByRunId(101L, "MODEL")).thenReturn(List.of(artifact));
        assertEquals(5_000_000_123L, tools.queryRunArtifacts("R-1", "MODEL").getArtifacts().get(0).fileSizeBytes());
    }

    @Test
    void existingRunWithoutArtifactsReturnsSuccess() {
        when(service.getByRunId(101L, null)).thenReturn(List.of());
        RunArtifactsToolResult result = tools.queryRunArtifacts("R-1", null);
        assertTrue(result.isSuccess());
        assertEquals("当前实验运行暂无实验产物", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
    }

    @Test
    void existingRunWithoutModelArtifactsReturnsSuccess() {
        when(service.getByRunId(101L, "MODEL")).thenReturn(List.of());
        RunArtifactsToolResult result = tools.queryRunArtifacts("R-1", "model");
        assertTrue(result.isSuccess());
        assertEquals("MODEL", result.getType());
        assertEquals("当前实验运行暂无 MODEL 类型实验产物", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
    }

    @Test
    void missingRunReturnsStructuredFailure() {
        when(resolver.getByRunCode("R-99999")).thenThrow(new BusinessException(404, "实验运行不存在"));
        RunArtifactsToolResult result = tools.queryRunArtifacts("R-99999", null);
        assertEquals("R-99999", result.getRunCode());
        assertNull(result.getType());
        assertFalse(result.isSuccess());
        assertEquals("运行 R-99999 不存在", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
    }

    @Test
    void unknownSystemFailureIsRethrown() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByRunId(101L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryRunArtifacts("R-1", null)));
    }

    @Test
    void unrelatedBusinessFailuresAreRethrown() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "实验产物不存在"))) {
            doThrow(failure).when(service).getByRunId(101L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryRunArtifacts("R-1", null)));
        }
    }

    @Test
    void delegatesExactlyOnceToExistingService() {
        when(service.getByRunId(101L, "MODEL")).thenReturn(List.of());
        tools.queryRunArtifacts("R-1", "model");
        verify(service).getByRunId(101L, "MODEL");
        verifyNoMoreInteractions(service);
    }

    @Test
    void toolDependsOnlyOnArtifactServiceWithoutMapperDependency() {
        assertArrayEquals(new Class<?>[]{ResultArtifactService.class, ExperimentRunService.class},
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
        assertEquals(List.of("runCode"), mapper.convertValue(schema.get("required"), List.class));
        when(service.getByRunId(101L, null)).thenReturn(List.of());
        var result = mapper.readTree(callback.call("{\"runCode\":\"R-1\"}"));
        assertTrue(result.get("success").asBoolean());
        assertEquals("当前实验运行暂无实验产物", result.get("message").asString());
        verify(service).getByRunId(101L, null);
    }

    @Test
    void springAiCallbackAcceptsExplicitNullType() {
        when(service.getByRunId(101L, null)).thenReturn(List.of());
        var result = JsonMapper.builder().build().readTree(
                ToolCallbacks.from(tools)[0].call("{\"runCode\":\"R-1\",\"type\":null}"));
        assertTrue(result.get("success").asBoolean());
        assertTrue(result.get("type").isNull());
        verify(service).getByRunId(101L, null);
    }

    private void assertSuccessfulQuery(String input, String expectedType) {
        List<ResultArtifactVO> artifacts = List.of(artifact(7L, expectedType == null ? "MODEL" : expectedType));
        when(service.getByRunId(101L, expectedType)).thenReturn(artifacts);
        RunArtifactsToolResult result = tools.queryRunArtifacts("R-1", input);
        assertEquals("R-1", result.getRunCode());
        assertEquals(expectedType, result.getType());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
        assertEquals(artifacts.stream().map(ArtifactToolItem::from).toList(), result.getArtifacts());
        verify(service).getByRunId(101L, expectedType);
    }

    private void assertInvalidType(String type) {
        RunArtifactsToolResult result = tools.queryRunArtifacts("R-1", type);
        assertFalse(result.isSuccess());
        assertEquals("实验产物类型不合法", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
        verifyNoInteractions(service, resolver);
    }

    private void assertInvalidRunCode(String runCode) {
        RunArtifactsToolResult result = tools.queryRunArtifacts(runCode, null);
        assertNull(result.getRunCode());
        assertFalse(result.isSuccess());
        assertEquals("实验运行编号不合法，请提供 R-1 格式的业务编号", result.getMessage());
        assertTrue(result.getArtifacts().isEmpty());
        verifyNoInteractions(service, resolver);
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
