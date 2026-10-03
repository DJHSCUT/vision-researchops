package com.djh.researchops;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.djh.researchops.controller.ResultArtifactController;
import com.djh.researchops.dto.CreateArtifactRequest;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.entity.ResultArtifact;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.mapper.ResultArtifactMapper;
import com.djh.researchops.service.ResultArtifactService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 只使用模拟 Mapper 和独立 MockMvc，不启动应用、不连接数据库。
class ResultArtifactServiceTests {

    private ResultArtifactMapper artifactMapper;
    private ExperimentRunMapper runMapper;
    private ResultArtifactService service;
    private MockMvc mvc;

    @BeforeAll
    static void initializeMapping() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ResultArtifact.class);
    }

    @BeforeEach
    void setUp() {
        artifactMapper = mock(ResultArtifactMapper.class);
        runMapper = mock(ExperimentRunMapper.class);
        service = new ResultArtifactService(artifactMapper, runMapper);
        when(runMapper.selectById(1L)).thenReturn(new ExperimentRun());
        mvc = MockMvcBuilders.standaloneSetup(new ResultArtifactController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createsArtifactWith201AndCompleteVO() throws Exception {
        mockInsertAndReload();
        mvc.perform(post("/api/runs/1/artifacts").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("训练完成点云", "POINT_CLOUD", "/results/run1/point_cloud.ply",
                                "30000 step 训练结果", "104857600")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("实验产物创建成功"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.runId").value(1))
                .andExpect(jsonPath("$.data.artifactName").value("训练完成点云"))
                .andExpect(jsonPath("$.data.artifactType").value("POINT_CLOUD"))
                .andExpect(jsonPath("$.data.storagePath").value("/results/run1/point_cloud.ply"))
                .andExpect(jsonPath("$.data.description").value("30000 step 训练结果"))
                .andExpect(jsonPath("$.data.fileSizeBytes").value(104857600))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
        var order = inOrder(runMapper, artifactMapper);
        order.verify(runMapper).selectById(1L);
        order.verify(artifactMapper).insert(any(ResultArtifact.class));
        order.verify(artifactMapper).selectById(10L);
    }

    @Test
    void missingRunCreateReturns404BeforeValidation() throws Exception {
        mvc.perform(post("/api/runs/99/artifacts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("实验运行不存在"));
        verifyNoInteractions(artifactMapper);
    }

    @Test
    void blankCreateNameReturns400() throws Exception {
        for (String name : new String[]{null, "", "   "}) {
            invalidCreate(createJson(name, "MODEL", "/result", null, "null"), "实验产物名称不能为空");
        }
    }

    @Test
    void invalidCreateTypeReturns400() throws Exception {
        for (String type : new String[]{null, "", " ", "model", "UNKNOWN"}) {
            invalidCreate(createJson("模型", type, "/result", null, "null"), "实验产物类型不合法");
        }
    }

    @Test
    void blankCreatePathReturns400() throws Exception {
        for (String path : new String[]{null, "", "   "}) {
            invalidCreate(createJson("模型", "MODEL", path, null, "null"), "实验产物路径不能为空");
        }
    }

    @Test
    void negativeCreateSizeReturns400() throws Exception {
        invalidCreate(createJson("模型", "MODEL", "/result", null, "-1"), "实验产物文件大小不能小于0");
    }

    @Test
    void acceptsAllSixTypesAndZeroSize() {
        mockInsertAndReload();
        for (String type : List.of("IMAGE", "MODEL", "POINT_CLOUD", "CHECKPOINT", "REPORT", "OTHER")) {
            CreateArtifactRequest request = request();
            request.setArtifactType(type);
            request.setFileSizeBytes(0L);
            var result = service.create(1L, request);
            assertEquals(type, result.getArtifactType());
            assertEquals(0L, result.getFileSizeBytes());
        }
    }

    @Test
    void acceptsOptionalNullDescriptionAndSize() {
        mockInsertAndReload();
        var result = service.create(1L, request());
        assertNull(result.getDescription());
        assertNull(result.getFileSizeBytes());
    }

    @Test
    void acceptsCreateLengthBoundaries() throws Exception {
        mockInsertAndReload();
        mvc.perform(post("/api/runs/1/artifacts").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("字".repeat(200), "REPORT", "p".repeat(1000), "字".repeat(500), "null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.artifactName").value("字".repeat(200)))
                .andExpect(jsonPath("$.data.storagePath").value("p".repeat(1000)))
                .andExpect(jsonPath("$.data.description").value("字".repeat(500)));
    }

    @Test
    void rejectsCreateName201() throws Exception {
        invalidCreate(createJson("字".repeat(201), "MODEL", "/result", null, "null"), "实验产物名称不能超过200个字符");
    }

    @Test
    void rejectsCreatePath1001() throws Exception {
        invalidCreate(createJson("模型", "MODEL", "p".repeat(1001), null, "null"), "实验产物路径不能超过1000个字符");
    }

    @Test
    void rejectsCreateDescription501() throws Exception {
        invalidCreate(createJson("模型", "MODEL", "/result", "字".repeat(501), "null"), "实验产物描述不能超过500个字符");
    }

    @Test
    void listsOnlyCurrentRunInDescendingIdOrder() throws Exception {
        mockList(null, List.of(artifact(3L), artifact(2L)));
        mvc.perform(get("/api/runs/1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[1].id").value(2));
        verify(runMapper).selectById(1L);
    }

    @Test
    void noArtifactsReturnsEmptyArray() throws Exception {
        mockList(null, List.of());
        mvc.perform(get("/api/runs/1/artifacts"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void filtersByExactTypeAndRun() throws Exception {
        mockList("MODEL", List.of(artifact(3L)));
        mvc.perform(get("/api/runs/1/artifacts").param("type", "MODEL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].artifactType").value("MODEL"));
    }

    @Test
    void invalidQueryTypeReturns400() throws Exception {
        for (String type : new String[]{"", " ", "model", "UNKNOWN"}) {
            mvc.perform(get("/api/runs/1/artifacts").param("type", type))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("实验产物类型不合法"));
        }
        verifyNoInteractions(artifactMapper);
    }

    @Test
    void missingRunListReturns404() throws Exception {
        mvc.perform(get("/api/runs/99/artifacts").param("type", "INVALID"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("实验运行不存在"));
        verifyNoInteractions(artifactMapper);
    }

    @Test
    void getsSingleArtifact() throws Exception {
        when(artifactMapper.selectById(10L)).thenReturn(artifact(10L));
        mvc.perform(get("/api/artifacts/10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void missingSingleArtifactReturns404() throws Exception {
        mvc.perform(get("/api/artifacts/99"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("实验产物不存在"));
    }

    @Test
    void patchesName() throws Exception {
        assertPatch("artifactName", "artifact_name", "最终点云");
    }

    @Test
    void patchesType() throws Exception {
        assertPatch("artifactType", "artifact_type", "POINT_CLOUD");
    }

    @Test
    void patchesStoragePath() throws Exception {
        assertPatch("storagePath", "storage_path", "/results/final/point_cloud.ply");
    }

    @Test
    void emptyDescriptionClearsDescription() throws Exception {
        assertPatch("description", "description", "");
    }

    @Test
    void patchesFileSizeIncludingZero() throws Exception {
        assertPatch("fileSizeBytes", "file_size_bytes", 104857600L);
        assertPatch("fileSizeBytes", "file_size_bytes", 0L);
    }

    @Test
    void emptyAndAllNullPatchReturns400() throws Exception {
        for (String json : new String[]{"{}", "{\"artifactName\":null,\"artifactType\":null,\"storagePath\":null,\"description\":null,\"fileSizeBytes\":null}",
                "{\"id\":99,\"runId\":99,\"createdAt\":\"2026-01-01T00:00:00\"}"}) {
            invalidPatch(json, "至少需要提供一个更新字段");
        }
    }

    @Test
    void missingPatchArtifactReturns404BeforeValidation() throws Exception {
        mvc.perform(patch("/api/artifacts/99").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("实验产物不存在"));
        verify(artifactMapper, never()).update(any(), any());
    }

    @Test
    void rejectsBlankPatchNameAndPath() throws Exception {
        for (String value : new String[]{"", "   "}) {
            invalidPatch("{\"artifactName\":" + jsonString(value) + "}", "实验产物名称不能为空");
            invalidPatch("{\"storagePath\":" + jsonString(value) + "}", "实验产物路径不能为空");
        }
    }

    @Test
    void rejectsInvalidPatchTypeAndNegativeSize() throws Exception {
        invalidPatch("{\"artifactType\":\"model\"}", "实验产物类型不合法");
        invalidPatch("{\"fileSizeBytes\":-1}", "实验产物文件大小不能小于0");
    }

    @Test
    void acceptsPatchLengthBoundaries() throws Exception {
        assertPatch("artifactName", "artifact_name", "字".repeat(200));
        assertPatch("storagePath", "storage_path", "p".repeat(1000));
        assertPatch("description", "description", "字".repeat(500));
    }

    @Test
    void rejectsPatchLengthsAboveLimits() throws Exception {
        invalidPatch("{\"artifactName\":" + jsonString("字".repeat(201)) + "}", "实验产物名称不能超过200个字符");
        invalidPatch("{\"storagePath\":" + jsonString("p".repeat(1001)) + "}", "实验产物路径不能超过1000个字符");
        invalidPatch("{\"description\":" + jsonString("字".repeat(501)) + "}", "实验产物描述不能超过500个字符");
    }

    @Test
    void concurrentDeletionDuringUpdateReturns404() throws Exception {
        when(artifactMapper.selectById(10L)).thenReturn(artifact(10L));
        mvc.perform(patch("/api/artifacts/10").contentType(MediaType.APPLICATION_JSON).content("{\"artifactName\":\"最终点云\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("实验产物不存在"));
    }

    @Test
    void deleteReturns204WithoutBody() throws Exception {
        when(artifactMapper.deleteById(10L)).thenReturn(1);
        mvc.perform(delete("/api/artifacts/10"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(artifactMapper).deleteById(10L);
    }

    @Test
    void missingDeleteReturns404() throws Exception {
        mvc.perform(delete("/api/artifacts/99"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("实验产物不存在"));
    }

    private CreateArtifactRequest request() {
        CreateArtifactRequest request = new CreateArtifactRequest();
        request.setArtifactName("模型");
        request.setArtifactType("MODEL");
        request.setStoragePath("/results/model.bin");
        return request;
    }

    private ResultArtifact artifact(Long id) {
        ResultArtifact artifact = new ResultArtifact();
        artifact.setId(id);
        artifact.setRunId(1L);
        artifact.setArtifactName("模型");
        artifact.setArtifactType("MODEL");
        artifact.setStoragePath("/results/model.bin");
        artifact.setDescription("原始描述");
        artifact.setFileSizeBytes(123L);
        artifact.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        artifact.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 13, 0));
        return artifact;
    }

    private void mockInsertAndReload() {
        when(artifactMapper.insert(any(ResultArtifact.class))).thenAnswer(invocation -> {
            ResultArtifact inserted = invocation.getArgument(0);
            assertEquals(1L, inserted.getRunId());
            assertNull(inserted.getCreatedAt());
            assertNull(inserted.getUpdatedAt());
            inserted.setId(10L);
            inserted.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            inserted.setUpdatedAt(inserted.getCreatedAt());
            when(artifactMapper.selectById(10L)).thenReturn(inserted);
            return 1;
        });
    }

    private void mockList(String type, List<ResultArtifact> records) {
        when(artifactMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ResultArtifact> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("run_id ="));
            assertTrue(sql.contains("ORDER BY id DESC"));
            assertEquals(type != null, sql.contains("artifact_type ="));
            assertFalse(sql.contains("LIKE"));
            assertEquals(type == null ? 1 : 2, query.getParamNameValuePairs().size());
            assertTrue(query.getParamNameValuePairs().containsValue(1L));
            if (type != null) assertTrue(query.getParamNameValuePairs().containsValue(type));
            return records;
        });
    }

    private void assertPatch(String field, String column, Object value) throws Exception {
        ResultArtifact saved = artifact(10L);
        switch (field) {
            case "artifactName" -> saved.setArtifactName((String) value);
            case "artifactType" -> saved.setArtifactType((String) value);
            case "storagePath" -> saved.setStoragePath((String) value);
            case "description" -> saved.setDescription((String) value);
            case "fileSizeBytes" -> saved.setFileSizeBytes((Long) value);
            default -> fail("未知测试字段");
        }
        when(artifactMapper.selectById(10L)).thenReturn(artifact(10L), saved);
        doAnswer(invocation -> {
            LambdaUpdateWrapper<ResultArtifact> update = invocation.getArgument(1);
            assertTrue(update.getSqlSegment().contains("id ="));
            assertEquals(column + "=", update.getSqlSet().split("#")[0]);
            assertEquals(2, update.getParamNameValuePairs().size());
            assertTrue(update.getParamNameValuePairs().containsValue(10L));
            assertTrue(update.getParamNameValuePairs().containsValue(value));
            return 1;
        }).when(artifactMapper).update(isNull(), any());
        mvc.perform(patch("/api/artifacts/10").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":" + (value instanceof String ? jsonString((String) value) : value) + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data." + field).value(value))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.runId").value(1))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    private void invalidCreate(String json, String message) throws Exception {
        mvc.perform(post("/api/runs/1/artifacts").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(artifactMapper);
    }

    private void invalidPatch(String json, String message) throws Exception {
        when(artifactMapper.selectById(10L)).thenReturn(artifact(10L));
        mvc.perform(patch("/api/artifacts/10").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
        verify(artifactMapper, never()).update(any(), any());
    }

    private String createJson(String name, String type, String path, String description, String size) {
        return "{\"artifactName\":" + jsonString(name) + ",\"artifactType\":" + jsonString(type)
                + ",\"storagePath\":" + jsonString(path) + ",\"description\":" + jsonString(description)
                + ",\"fileSizeBytes\":" + size + "}";
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
