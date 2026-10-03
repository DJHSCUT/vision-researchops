package com.djh.researchops;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.djh.researchops.controller.ExperimentMetricController;
import com.djh.researchops.dto.CreateMetricRequest;
import com.djh.researchops.entity.ExperimentMetric;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import com.djh.researchops.mapper.ExperimentMetricMapper;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.service.ExperimentMetricService;
import com.djh.researchops.vo.ExperimentMetricVO;
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

// 模拟 Mapper + 独立 MockMvc，不启动应用，不连接真实数据库。
class ExperimentMetricServiceTests {

    private ExperimentMetricMapper metricMapper;
    private ExperimentRunMapper runMapper;
    private ExperimentMetricService service;
    private MockMvc mvc;

    @BeforeAll
    static void initializeMapping() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExperimentMetric.class
        );
    }

    @BeforeEach
    void setUp() {
        metricMapper = mock(ExperimentMetricMapper.class);
        runMapper = mock(ExperimentRunMapper.class);
        service = new ExperimentMetricService(metricMapper, runMapper);
        when(runMapper.selectById(1L)).thenReturn(new ExperimentRun());
        mvc = MockMvcBuilders.standaloneSetup(new ExperimentMetricController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsPsnrWith201AndCompleteVO() throws Exception {
        mockInsertAndReload();
        mvc.perform(post("/api/runs/1/metrics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricName\":\"PSNR\",\"metricValue\":28.43,\"unit\":\"dB\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("实验指标创建成功"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.runId").value(1))
                .andExpect(jsonPath("$.data.metricName").value("PSNR"))
                .andExpect(jsonPath("$.data.metricValue").value(28.43))
                .andExpect(jsonPath("$.data.unit").value("dB"))
                .andExpect(jsonPath("$.data.createdAt").exists());
        var order = inOrder(runMapper, metricMapper);
        order.verify(runMapper).selectById(1L);
        order.verify(metricMapper).insert(any(ExperimentMetric.class));
        order.verify(metricMapper).selectById(10L);
    }

    @Test
    void createsTrainLossWithStep() throws Exception {
        mockInsertAndReload();
        mvc.perform(post("/api/runs/1/metrics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricName\":\"train_loss\",\"metricValue\":0.0037,\"step\":30000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.metricName").value("train_loss"))
                .andExpect(jsonPath("$.data.metricValue").value(0.0037))
                .andExpect(jsonPath("$.data.step").value(30000));
    }

    @Test
    void nullOrMissingNameReturns400() throws Exception {
        assertInvalidCreate("{\"metricName\":null,\"metricValue\":1}", "实验指标名称不能为空");
        assertInvalidCreate("{\"metricValue\":1}", "实验指标名称不能为空");
    }

    @Test
    void emptyNameReturns400() throws Exception {
        assertInvalidCreate("{\"metricName\":\"\",\"metricValue\":1}", "实验指标名称不能为空");
    }

    @Test
    void whitespaceNameReturns400() throws Exception {
        assertInvalidCreate("{\"metricName\":\"   \",\"metricValue\":1}", "实验指标名称不能为空");
    }

    @Test
    void oversizedNameReturns400() throws Exception {
        assertInvalidCreate("{\"metricName\":\"" + "字".repeat(101) + "\",\"metricValue\":1}",
                "实验指标名称不能超过100个字符");
    }

    @Test
    void nullOrMissingValueReturns400() throws Exception {
        assertInvalidCreate("{\"metricName\":\"PSNR\",\"metricValue\":null}", "实验指标数值不能为空");
        assertInvalidCreate("{\"metricName\":\"PSNR\"}", "实验指标数值不能为空");
    }

    @Test
    void zeroValueSucceeds() {
        mockInsertAndReload();
        assertEquals(0.0, service.create(1L, request("PSNR", 0.0)).getMetricValue());
    }

    @Test
    void negativeValueSucceeds() {
        mockInsertAndReload();
        assertEquals(-0.5, service.create(1L, request("PSNR", -0.5)).getMetricValue());
    }

    @Test
    void oversizedUnitReturns400() throws Exception {
        assertInvalidCreate("{\"metricName\":\"PSNR\",\"metricValue\":1,\"unit\":\""
                + "字".repeat(51) + "\"}", "实验指标单位不能超过50个字符");
    }

    @Test
    void negativeStepReturns400() throws Exception {
        assertInvalidCreate("{\"metricName\":\"train_loss\",\"metricValue\":1,\"step\":-1}",
                "实验指标步数不能小于0");
    }

    @Test
    void createMissingRunReturns404() throws Exception {
        mvc.perform(post("/api/runs/99/metrics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricName\":\"PSNR\",\"metricValue\":28.43}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("实验运行不存在"));
        verifyNoInteractions(metricMapper);
    }

    @Test
    void queriesAllMetricsWithRunScopeAndDescendingId() throws Exception {
        ExperimentMetric newer = metric(3L, "PSNR", null);
        ExperimentMetric older = metric(2L, "SSIM", null);
        when(metricMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ExperimentMetric> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("run_id ="));
            assertFalse(sql.contains("metric_name ="));
            assertTrue(sql.contains("ORDER BY id DESC"));
            assertEquals(List.of(1L), List.copyOf(query.getParamNameValuePairs().values()));
            return List.of(newer, older);
        });
        mvc.perform(get("/api/runs/1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[0].runId").value(1))
                .andExpect(jsonPath("$.data[0].metricValue").value(28.43))
                .andExpect(jsonPath("$.data[0].unit").value("dB"))
                .andExpect(jsonPath("$.data[0].createdAt").exists());
        verify(runMapper).selectById(1L);
    }

    @Test
    void noMetricsReturnsEmptyList() throws Exception {
        when(metricMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.getByRunId(1L, null).isEmpty());
        mvc.perform(get("/api/runs/1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void filtersPsnrByExactName() throws Exception {
        mockFilteredQuery("PSNR", List.of(metric(3L, "PSNR", null)));
        mvc.perform(get("/api/runs/1/metrics").param("name", "PSNR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].metricName").value("PSNR"));
    }

    @Test
    void emptyQueryNameReturns400() throws Exception {
        assertInvalidQueryName("", "实验指标名称不能为空");
    }

    @Test
    void whitespaceQueryNameReturns400() throws Exception {
        assertInvalidQueryName("   ", "实验指标名称不能为空");
    }

    @Test
    void trainLossOrdersByStepThenIdAndPreservesNullStep() throws Exception {
        mockFilteredQuery("train_loss", List.of(
                metric(5L, "train_loss", null),
                metric(4L, "train_loss", 1000L),
                metric(2L, "train_loss", 5000L),
                metric(6L, "train_loss", 5000L),
                metric(3L, "train_loss", 10000L),
                metric(1L, "train_loss", 30000L)));
        List<ExperimentMetricVO> result = service.getByRunId(1L, "train_loss");
        assertEquals(List.of(5L, 4L, 2L, 6L, 3L, 1L),
                result.stream().map(ExperimentMetricVO::getId).toList());
        assertNull(result.get(0).getStep());
        mvc.perform(get("/api/runs/1/metrics").param("name", "train_loss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].step").value(1000))
                .andExpect(jsonPath("$.data[5].step").value(30000));
    }

    @Test
    void queryMissingRunReturns404BeforeMetricQuery() throws Exception {
        mvc.perform(get("/api/runs/99/metrics").param("name", "PSNR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("实验运行不存在"));
        mvc.perform(get("/api/runs/99/metrics"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(metricMapper);
    }

    @Test
    void acceptsNameAndUnitLimitsWithZeroStep() {
        mockInsertAndReload();
        CreateMetricRequest request = request("字".repeat(100), 1.0);
        request.setUnit("字".repeat(50));
        request.setStep(0L);
        ExperimentMetricVO result = service.create(1L, request);
        assertEquals(request.getMetricName(), result.getMetricName());
        assertEquals(request.getUnit(), result.getUnit());
        assertEquals(0L, result.getStep());
    }

    @Test
    void acceptsNullAndEmptyUnitAndNullStep() throws Exception {
        mockInsertAndReload();
        for (String fields : new String[]{"", ",\"unit\":null,\"step\":null", ",\"unit\":\"\""}) {
            mvc.perform(post("/api/runs/1/metrics").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"metricName\":\"SSIM\",\"metricValue\":0.912" + fields + "}"))
                    .andExpect(status().isCreated());
        }
        CreateMetricRequest request = request("SSIM", 0.912);
        assertNull(service.create(1L, request).getUnit());
        request.setUnit("");
        assertEquals("", service.create(1L, request).getUnit());
        assertNull(service.create(1L, request).getStep());
    }

    @Test
    void serviceIndependentlyValidatesCreateParameters() {
        for (String name : new String[]{null, "", "   "}) {
            assertServiceInvalid(request(name, 1.0), "实验指标名称不能为空");
        }
        assertServiceInvalid(request("字".repeat(101), 1.0), "实验指标名称不能超过100个字符");
        assertServiceInvalid(request("PSNR", null), "实验指标数值不能为空");
        CreateMetricRequest oversizedUnit = request("PSNR", 1.0);
        oversizedUnit.setUnit("字".repeat(51));
        assertServiceInvalid(oversizedUnit, "实验指标单位不能超过50个字符");
        CreateMetricRequest negativeStep = request("train_loss", 1.0);
        negativeStep.setStep(-1L);
        assertServiceInvalid(negativeStep, "实验指标步数不能小于0");
        verifyNoInteractions(metricMapper);
    }

    @Test
    void oversizedQueryNameReturns400() throws Exception {
        assertInvalidQueryName("字".repeat(101), "实验指标名称不能超过100个字符");
    }

    @Test
    void updatesValueFrom28Point8To28Point9() throws Exception {
        ExperimentMetric original = metric(10L, "PSNR", null);
        original.setMetricValue(28.8);
        ExperimentMetric saved = metric(10L, "PSNR", null);
        saved.setMetricValue(28.9);
        assertPatch("{\"metricValue\":28.9}", "metric_value", 28.9, original, saved);
    }

    @Test
    void updatesValueToZeroAndNegative() throws Exception {
        for (double value : new double[]{0.0, -2.7}) {
            ExperimentMetric saved = metric(10L, "PSNR", null);
            saved.setMetricValue(value);
            assertPatch("{\"metricValue\":" + value + "}", "metric_value", value,
                    metric(10L, "PSNR", null), saved);
        }
    }

    @Test
    void updatesName() throws Exception {
        assertPatch("{\"metricName\":\"SSIM\"}", "metric_name", "SSIM",
                metric(10L, "PSNR", null), metric(10L, "SSIM", null));
    }

    @Test
    void updatesUnit() throws Exception {
        ExperimentMetric saved = metric(10L, "PSNR", null);
        saved.setUnit("ms");
        assertPatch("{\"unit\":\"ms\"}", "unit", "ms", metric(10L, "PSNR", null), saved);
    }

    @Test
    void emptyUnitClearsUnit() throws Exception {
        ExperimentMetric saved = metric(10L, "PSNR", null);
        saved.setUnit("");
        assertPatch("{\"unit\":\"\"}", "unit", "", metric(10L, "PSNR", null), saved);
    }

    @Test
    void updatesStep() throws Exception {
        assertPatch("{\"step\":5000}", "step", 5000L,
                metric(10L, "train_loss", 1000L), metric(10L, "train_loss", 5000L));
    }

    @Test
    void explicitNullStepClearsStep() throws Exception {
        assertPatch("{\"step\":null}", "step", null,
                metric(10L, "train_loss", 5000L), metric(10L, "train_loss", null));
    }

    @Test
    void omittedStepPreservesSeriesAndUpdatesOnlyOneRecord() throws Exception {
        ExperimentMetric original = metric(10L, "train_loss", 5000L);
        original.setMetricValue(0.012);
        ExperimentMetric saved = metric(10L, "train_loss", 5000L);
        saved.setMetricValue(0.0115);
        assertPatch("{\"metricValue\":0.0115}", "metric_value", 0.0115, original, saved);
    }

    @Test
    void blankUpdateNameReturns400() throws Exception {
        for (String name : new String[]{"", "   "}) {
            assertInvalidPatch("{\"metricName\":\"" + name + "\"}", "实验指标名称不能为空");
        }
    }

    @Test
    void oversizedUpdateNameReturns400() throws Exception {
        assertInvalidPatch("{\"metricName\":\"" + "字".repeat(101) + "\"}", "实验指标名称不能超过100个字符");
    }

    @Test
    void oversizedUpdateUnitReturns400() throws Exception {
        assertInvalidPatch("{\"unit\":\"" + "字".repeat(51) + "\"}", "实验指标单位不能超过50个字符");
    }

    @Test
    void negativeUpdateStepReturns400() throws Exception {
        assertInvalidPatch("{\"step\":-1}", "实验指标步数不能小于0");
    }

    @Test
    void emptyOrIneffectivePatchReturns400() throws Exception {
        for (String json : new String[]{"{}", "{\"metricName\":null,\"metricValue\":null,\"unit\":null}",
                "{\"id\":99,\"runId\":99,\"createdAt\":\"2026-01-01T00:00:00\"}",
                "{\"stepProvided\":true}"}) {
            assertInvalidPatch(json, "至少需要提供一个更新字段");
        }
    }

    @Test
    void updateMissingMetricReturns404BeforeValidation() throws Exception {
        mvc.perform(patch("/api/metrics/99").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("实验指标不存在"));
        verify(metricMapper, never()).update(any(), any());
    }

    @Test
    void concurrentRemovalDuringUpdateReturns404() throws Exception {
        when(metricMapper.selectById(10L)).thenReturn(metric(10L, "PSNR", null));
        when(metricMapper.update(isNull(), any())).thenReturn(0);
        mvc.perform(patch("/api/metrics/10").contentType(MediaType.APPLICATION_JSON).content("{\"metricValue\":28.9}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("实验指标不存在"));
    }

    @Test
    void deletesMetricWith204AndNoBody() throws Exception {
        when(metricMapper.deleteById(10L)).thenReturn(1);
        mvc.perform(delete("/api/metrics/10"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(metricMapper).deleteById(10L);
    }

    @Test
    void deleteMissingMetricReturns404() throws Exception {
        mvc.perform(delete("/api/metrics/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("实验指标不存在"));
    }

    @Test
    void deletedRecordIsAbsentFromSubsequentQuery() throws Exception {
        var records = new java.util.ArrayList<>(List.of(metric(10L, "a", null), metric(11L, "PSNR", null)));
        when(metricMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(records));
        when(metricMapper.deleteById(10L)).thenAnswer(invocation -> {
            records.removeIf(record -> record.getId().equals(10L));
            return 1;
        });
        assertEquals(2, service.getByRunId(1L, null).size());
        mvc.perform(delete("/api/metrics/10")).andExpect(status().isNoContent());
        mvc.perform(get("/api/runs/1/metrics"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].metricName").value("PSNR"));
    }

    private void assertPatch(String json, String column, Object value,
                             ExperimentMetric original, ExperimentMetric saved) throws Exception {
        when(metricMapper.selectById(10L)).thenReturn(original, saved);
        doAnswer(invocation -> {
            LambdaUpdateWrapper<ExperimentMetric> update = invocation.getArgument(1);
            assertTrue(update.getSqlSegment().contains("id ="));
            assertEquals(column + "=", update.getSqlSet().split("#")[0]);
            assertFalse(update.getSqlSet().contains("run_id"));
            assertFalse(update.getSqlSet().contains("created_at"));
            assertEquals(2, update.getParamNameValuePairs().size());
            assertTrue(update.getParamNameValuePairs().containsValue(10L));
            assertTrue(update.getParamNameValuePairs().containsValue(value));
            return 1;
        }).when(metricMapper).update(isNull(), any());
        mvc.perform(patch("/api/metrics/10").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.runId").value(1))
                .andExpect(jsonPath("$.data.metricName").value(saved.getMetricName()))
                .andExpect(jsonPath("$.data.metricValue").value(saved.getMetricValue()))
                .andExpect(jsonPath("$.data.unit").value(saved.getUnit()))
                .andExpect(jsonPath("$.data.step").value(saved.getStep()));
    }

    private void assertInvalidPatch(String json, String message) throws Exception {
        when(metricMapper.selectById(10L)).thenReturn(metric(10L, "PSNR", null));
        mvc.perform(patch("/api/metrics/10").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
        verify(metricMapper, never()).update(any(), any());
    }

    private CreateMetricRequest request(String name, Double value) {
        CreateMetricRequest request = new CreateMetricRequest();
        request.setMetricName(name);
        request.setMetricValue(value);
        return request;
    }

    private ExperimentMetric metric(Long id, String name, Long step) {
        ExperimentMetric metric = new ExperimentMetric();
        metric.setId(id);
        metric.setRunId(1L);
        metric.setMetricName(name);
        metric.setMetricValue(28.43);
        metric.setUnit("dB");
        metric.setStep(step);
        metric.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        return metric;
    }

    private void mockInsertAndReload() {
        when(metricMapper.insert(any(ExperimentMetric.class))).thenAnswer(invocation -> {
            ExperimentMetric inserted = invocation.getArgument(0);
            assertEquals(1L, inserted.getRunId());
            assertNull(inserted.getCreatedAt());
            inserted.setId(10L);
            ExperimentMetric saved = metric(10L, inserted.getMetricName(), inserted.getStep());
            saved.setMetricValue(inserted.getMetricValue());
            saved.setUnit(inserted.getUnit());
            when(metricMapper.selectById(10L)).thenReturn(saved);
            return 1;
        });
    }

    private void mockFilteredQuery(String name, List<ExperimentMetric> metrics) {
        when(metricMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ExperimentMetric> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("run_id ="));
            assertTrue(sql.contains("metric_name ="));
            assertFalse(sql.contains("LIKE"));
            assertTrue(sql.contains("ORDER BY step ASC,id ASC"));
            assertEquals(2, query.getParamNameValuePairs().size());
            assertTrue(query.getParamNameValuePairs().containsValue(1L));
            assertTrue(query.getParamNameValuePairs().containsValue(name));
            return metrics;
        });
    }

    private void assertInvalidCreate(String json, String message) throws Exception {
        mvc.perform(post("/api/runs/1/metrics").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(runMapper, metricMapper);
    }

    private void assertInvalidQueryName(String name, String message) throws Exception {
        mvc.perform(get("/api/runs/1/metrics").param("name", name))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message));
        verify(runMapper).selectById(1L);
        verifyNoInteractions(metricMapper);
    }

    private void assertServiceInvalid(CreateMetricRequest request, String message) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(1L, request));
        assertEquals(400, error.getCode());
        assertEquals(message, error.getMessage());
    }
}
