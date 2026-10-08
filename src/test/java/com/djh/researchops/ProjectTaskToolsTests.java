package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.*;
import com.djh.researchops.tool.ProjectTaskTools;
import com.djh.researchops.util.BusinessCodeParser;
import com.djh.researchops.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTaskToolsTests {
    private ResearchProjectService projects;
    private ExperimentTaskService tasks;
    private ProjectTaskTools tools;

    @BeforeEach
    void setUp() {
        projects = mock(ResearchProjectService.class);
        tasks = mock(ExperimentTaskService.class);
        var project = new ResearchProjectVO(); project.setId(303L); project.setProjectCode("P-1");
        when(projects.getByProjectCode("P-1")).thenReturn(project);
        tools = new ProjectTaskTools(projects, tasks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P-1", "p-1", "P1", "p1", "  p1 \t"})
    void normalizesAndQueriesRealTaskCode(String code) {
        var task = new ExperimentTaskVO(); task.setId(909L); task.setProjectId(303L);
        task.setTaskCode("T-47"); task.setName("真实任务"); task.setStatus("TODO");
        task.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4)); task.setUpdatedAt(task.getCreatedAt().plusDays(1));
        when(tasks.getByProjectId(303L, null)).thenReturn(List.of(task));
        var result = tools.queryProjectTasks(code, null);
        assertTrue(result.isSuccess()); assertEquals("P-1", result.getProjectCode()); assertNull(result.getStatus());
        assertEquals(List.of(TaskToolItem.from(task)), result.getTasks());
        assertEquals("T-47", result.getTasks().get(0).taskCode());
        verify(projects).getByProjectCode("P-1"); verify(tasks).getByProjectId(303L, null);
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(result));
        assertEquals(5, json.size());
        var item = json.get("tasks").get(0);
        assertEquals(5, item.size());
        for (String field : List.of("id", "taskId", "projectId")) {
            assertFalse(json.has(field)); assertFalse(item.has(field));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "P-0", "P--1", "P-abc", "P-001", "P-9223372036854775808", "P-999999999999999999999", "Project 1"})
    void invalidCodeStopsBeforeServices(String code) {
        assertNull(BusinessCodeParser.normalizeProjectCode(code));
        var result = tools.queryProjectTasks(code, null);
        assertFalse(result.isSuccess()); assertTrue(result.getTasks().isEmpty());
        verifyNoInteractions(projects, tasks);
    }

    @Test
    void acceptsLongMaximum() {
        assertEquals("P-9223372036854775807", BusinessCodeParser.normalizeProjectCode("p9223372036854775807"));
    }

    @Test
    void missingProjectStopsBeforeTasks() {
        when(projects.getByProjectCode("P-99999")).thenThrow(new BusinessException(404, "研究项目不存在"));
        var result = tools.queryProjectTasks("P-99999", null);
        assertFalse(result.isSuccess()); assertEquals("研究项目 P-99999 不存在", result.getMessage());
        assertTrue(result.getTasks().isEmpty()); verifyNoInteractions(tasks);
    }

    @Test
    void emptyProjectSucceeds() {
        when(tasks.getByProjectId(303L, null)).thenReturn(List.of());
        var result = tools.queryProjectTasks("P-1", null);
        assertTrue(result.isSuccess()); assertTrue(result.getTasks().isEmpty());
        assertEquals("P-1 当前没有实验任务。", result.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TODO", "RUNNING", "COMPLETED", "FAILED", " todo "})
    void filtersAllowedStatus(String input) {
        String status = input.trim().toUpperCase(java.util.Locale.ROOT);
        when(tasks.getByProjectId(303L, status)).thenReturn(List.of());
        var result = tools.queryProjectTasks("P-1", input);
        assertTrue(result.isSuccess()); assertEquals(status, result.getStatus());
        assertEquals("P-1 当前没有 " + status + " 状态的实验任务。", result.getMessage());
        verify(tasks).getByProjectId(303L, status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "DONE", "CANCELLED", "", " ", "失败"})
    void invalidStatusStopsBeforeServices(String status) {
        var result = tools.queryProjectTasks("P-1", status);
        assertFalse(result.isSuccess()); assertEquals("实验任务状态不合法", result.getMessage());
        verifyNoInteractions(projects, tasks);
    }

    @Test
    void failuresAreNotDisguisedAsMissingProject() {
        var failure = new BusinessException(500, "内部错误");
        when(projects.getByProjectCode("P-1")).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryProjectTasks("P-1", null)));
        verifyNoInteractions(tasks);
    }

    @Test
    void serviceOrderIsPreservedForStableTies() {
        var first = new ExperimentTaskVO(); first.setTaskCode("T-8");
        var second = new ExperimentTaskVO(); second.setTaskCode("T-2");
        when(tasks.getByProjectId(303L, null)).thenReturn(List.of(first, second));
        assertEquals(List.of(TaskToolItem.from(first), TaskToolItem.from(second)), tools.queryProjectTasks("P-1", null).getTasks());
    }

    @Test
    void dependsOnlyOnServices() {
        assertArrayEquals(new Class<?>[]{ResearchProjectService.class, ExperimentTaskService.class}, ProjectTaskTools.class.getConstructors()[0].getParameterTypes());
        assertTrue(Arrays.stream(ProjectTaskTools.class.getDeclaredFields()).noneMatch(f -> f.getType().getPackageName().contains(".mapper")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"projectCode\":\"P-1\"}", "{\"projectCode\":\"P-1\",\"status\":null}"})
    void springAiSchemaAndCallbackSupportOptionalStatus(String arguments) {
        when(tasks.getByProjectId(303L, null)).thenReturn(List.of());
        var callback = ToolCallbacks.from(tools)[0];
        assertEquals("queryProjectTasks", callback.getToolDefinition().name());
        var mapper = JsonMapper.builder().build();
        var schema = mapper.readTree(callback.getToolDefinition().inputSchema());
        assertEquals(List.of("projectCode"), mapper.convertValue(schema.get("required"), List.class));
        assertFalse(schema.get("properties").has("projectId"));
        var result = mapper.readTree(callback.call(arguments));
        assertTrue(result.get("success").asBoolean()); assertTrue(result.get("status").isNull());
        verify(tasks).getByProjectId(303L, null);
    }
}
