package com.djh.researchops;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.djh.researchops.controller.ExperimentLogController;
import com.djh.researchops.dto.CreateLogRequest;
import com.djh.researchops.entity.ExperimentLog;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import com.djh.researchops.mapper.ExperimentLogMapper;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.service.ExperimentLogService;
import com.djh.researchops.vo.ExperimentLogVO;
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
class ExperimentLogServiceTests {

    private ExperimentLogMapper logMapper;
    private ExperimentRunMapper runMapper;
    private ExperimentLogService service;
    private MockMvc mvc;

    @BeforeAll
    static void initializeMapping() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExperimentLog.class
        );
    }

    @BeforeEach
    void setUp() {
        logMapper = mock(ExperimentLogMapper.class);
        runMapper = mock(ExperimentRunMapper.class);
        service = new ExperimentLogService(logMapper, runMapper);
        when(runMapper.selectById(1L)).thenReturn(new ExperimentRun());
        mvc = MockMvcBuilders.standaloneSetup(new ExperimentLogController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsInfoLog() {
        assertCreatedLevel("INFO", "INFO");
    }

    @Test
    void nullLevelDefaultsToInfo() {
        assertCreatedLevel(null, "INFO");
    }

    @Test
    void createsWarnLog() {
        assertCreatedLevel("WARN", "WARN");
    }

    @Test
    void createsErrorLog() {
        assertCreatedLevel("ERROR", "ERROR");
    }

    @Test
    void rejectsInvalidCreateLevelWith400() throws Exception {
        for (String level : new String[]{"DEBUG", "ABC", "", "   ", "info", " INFO "}) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.create(1L, request(level)));
            assertEquals(400, error.getCode());
            assertEquals("实验日志级别不合法", error.getMessage());
        }
        mvc.perform(post("/api/runs/1/logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"DEBUG\",\"content\":\"日志\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("实验日志级别不合法"));
        verifyNoInteractions(logMapper);
    }

    @Test
    void nullOrMissingContentReturns400() throws Exception {
        assertInvalidContent("{\"content\":null}", "实验日志内容不能为空");
        assertInvalidContent("{}", "实验日志内容不能为空");
    }

    @Test
    void emptyContentReturns400() throws Exception {
        assertInvalidContent("{\"content\":\"\"}", "实验日志内容不能为空");
    }

    @Test
    void whitespaceContentReturns400() throws Exception {
        assertInvalidContent("{\"content\":\"   \"}", "实验日志内容不能为空");
    }

    @Test
    void oversizedContentReturns400() throws Exception {
        assertInvalidContent("{\"content\":\"" + "字".repeat(5001) + "\"}",
                "实验日志内容不能超过5000个字符");
    }

    @Test
    void createMissingRunReturns404() throws Exception {
        mvc.perform(post("/api/runs/99/logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"日志\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("实验运行不存在"));
        verifyNoInteractions(logMapper);
    }

    @Test
    void queriesAllLogsWithRunScopeAndDescendingOrder() {
        ExperimentLog newer = log(3L, "ERROR");
        ExperimentLog older = log(2L, "INFO");
        when(logMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ExperimentLog> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("run_id ="));
            assertFalse(sql.contains("level ="));
            assertTrue(sql.contains("ORDER BY id DESC"));
            assertEquals(List.of(1L), List.copyOf(query.getParamNameValuePairs().values()));
            return List.of(newer, older);
        });

        List<ExperimentLogVO> result = service.getByRunId(1L, null);

        assertEquals(List.of(3L, 2L), result.stream().map(ExperimentLogVO::getId).toList());
        assertEquals("ERROR", result.get(0).getLevel());
        assertEquals(newer.getContent(), result.get(0).getContent());
        assertEquals(newer.getCreatedAt(), result.get(0).getCreatedAt());
        verify(runMapper).selectById(1L);
    }

    @Test
    void filtersErrorLevel() throws Exception {
        when(logMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ExperimentLog> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("run_id ="));
            assertTrue(sql.contains("level ="));
            assertTrue(sql.contains("ORDER BY id DESC"));
            assertEquals(2, query.getParamNameValuePairs().size());
            assertTrue(query.getParamNameValuePairs().containsValue(1L));
            assertTrue(query.getParamNameValuePairs().containsValue("ERROR"));
            return List.of(log(3L, "ERROR"));
        });

        mvc.perform(get("/api/runs/1/logs").param("level", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].level").value("ERROR"));
    }

    @Test
    void queryMissingRunReturns404() throws Exception {
        mvc.perform(get("/api/runs/99/logs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("实验运行不存在"));
        verifyNoInteractions(logMapper);
    }

    @Test
    void noLogsReturnsEmptyList() throws Exception {
        when(logMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.getByRunId(1L, null).isEmpty());
        mvc.perform(get("/api/runs/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidQueryLevelReturns400() throws Exception {
        for (String level : new String[]{"DEBUG", "ABC", "", "   ", "error"}) {
            mvc.perform(get("/api/runs/1/logs").param("level", level))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("实验日志级别不合法"));
        }
        verifyNoInteractions(logMapper);
    }

    @Test
    void omittedAndNullLevelCreate201AtContentLimit() throws Exception {
        mockInsertAndReload();
        for (String prefix : new String[]{"{", "{\"level\":null,"}) {
            mvc.perform(post("/api/runs/1/logs").contentType(MediaType.APPLICATION_JSON)
                            .content(prefix + "\"content\":\"" + "字".repeat(5000) + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message").value("实验日志创建成功"))
                    .andExpect(jsonPath("$.data.level").value("INFO"));
        }
    }

    private CreateLogRequest request(String level) {
        CreateLogRequest request = new CreateLogRequest();
        request.setLevel(level);
        request.setContent("实验开始\n保留日志原始内容");
        return request;
    }

    private ExperimentLog log(Long id, String level) {
        ExperimentLog log = new ExperimentLog();
        log.setId(id);
        log.setRunId(1L);
        log.setLevel(level);
        log.setContent("日志内容");
        log.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        return log;
    }

    private void mockInsertAndReload() {
        when(logMapper.insert(any(ExperimentLog.class))).thenAnswer(invocation -> {
            ExperimentLog inserted = invocation.getArgument(0);
            assertEquals(1L, inserted.getRunId());
            assertNull(inserted.getCreatedAt());
            inserted.setId(10L);
            ExperimentLog saved = log(10L, inserted.getLevel());
            saved.setContent(inserted.getContent());
            when(logMapper.selectById(10L)).thenReturn(saved);
            return 1;
        });
    }

    private void assertCreatedLevel(String level, String expectedLevel) {
        mockInsertAndReload();
        CreateLogRequest request = request(level);

        ExperimentLogVO result = service.create(1L, request);

        assertEquals(10L, result.getId());
        assertEquals(1L, result.getRunId());
        assertEquals(expectedLevel, result.getLevel());
        assertEquals(request.getContent(), result.getContent());
        assertNotNull(result.getCreatedAt());
        var order = inOrder(runMapper, logMapper);
        order.verify(runMapper).selectById(1L);
        order.verify(logMapper).insert(any(ExperimentLog.class));
        order.verify(logMapper).selectById(10L);
    }

    private void assertInvalidContent(String json, String message) throws Exception {
        mvc.perform(post("/api/runs/1/logs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(runMapper, logMapper);
    }
}
