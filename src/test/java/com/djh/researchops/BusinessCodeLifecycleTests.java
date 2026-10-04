package com.djh.researchops;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.djh.researchops.controller.ExperimentRunController;
import com.djh.researchops.controller.ExperimentTaskController;
import com.djh.researchops.controller.ResearchProjectController;
import com.djh.researchops.dto.*;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.entity.ExperimentTask;
import com.djh.researchops.entity.ResearchProject;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.mapper.ExperimentTaskMapper;
import com.djh.researchops.mapper.ResearchProjectMapper;
import com.djh.researchops.service.BusinessCodeService;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.service.ExperimentTaskService;
import com.djh.researchops.service.ResearchProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Mock Mapper 验证创建、更新、删除、VO 和原 REST URL；无真实数据库写入。
class BusinessCodeLifecycleTests {

    @BeforeAll
    static void initializeMapping() {
        for (Class<?> entity : List.of(ResearchProject.class, ExperimentTask.class, ExperimentRun.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
    }

    private BusinessCodeService codes;
    private ResearchProjectMapper projects;
    private ExperimentTaskMapper tasks;
    private ExperimentRunMapper runs;
    private ResearchProjectService projectService;
    private ExperimentTaskService taskService;
    private ExperimentRunService runService;
    private ResearchProject project;
    private ExperimentTask task;
    private ExperimentRun run;

    @BeforeEach
    void setUp() {
        codes = mock(BusinessCodeService.class);
        projects = mock(ResearchProjectMapper.class);
        tasks = mock(ExperimentTaskMapper.class);
        runs = mock(ExperimentRunMapper.class);
        projectService = new ResearchProjectService(projects, codes);
        taskService = new ExperimentTaskService(tasks, projects, codes);
        runService = new ExperimentRunService(runs, tasks, codes);
        project = new ResearchProject(); project.setId(17L); project.setProjectCode("P-6"); project.setName("测试项目");
        task = new ExperimentTask(); task.setId(24L); task.setProjectId(17L); task.setTaskCode("T-8"); task.setName("测试任务");
        run = new ExperimentRun(); run.setId(99L); run.setTaskId(24L); run.setRunCode("R-13"); run.setRunName("测试运行"); run.setStatus("PENDING");
        when(projects.selectById(17L)).thenAnswer(invocation -> project);
        when(tasks.selectById(24L)).thenAnswer(invocation -> task);
        when(runs.selectById(99L)).thenAnswer(invocation -> run);
        when(projects.insert(any(ResearchProject.class))).thenAnswer(invocation -> {
            project = invocation.getArgument(0); project.setId(17L); return 1;
        });
        when(tasks.insert(any(ExperimentTask.class))).thenAnswer(invocation -> {
            task = invocation.getArgument(0); task.setId(24L); return 1;
        });
        when(runs.insert(any(ExperimentRun.class))).thenAnswer(invocation -> {
            run = invocation.getArgument(0); run.setId(99L); return 1;
        });
    }

    @Test
    void projectCreateGeneratesCodeUnrelatedToPrimaryKeyAndReturnsIt() {
        when(codes.nextProjectCode()).thenReturn("P-7");
        var result = projectService.create(projectRequest());
        assertEquals(17L, result.getId());
        assertEquals("P-7", result.getProjectCode());
        assertEquals("P-7", project.getProjectCode());
        assertEquals("ACTIVE", result.getStatus());
        verify(codes).nextProjectCode();
    }

    @Test
    void taskCreateGeneratesCodeAndPreservesProjectAssociation() {
        when(codes.nextTaskCode()).thenReturn("T-9");
        var result = taskService.create(17L, taskRequest());
        assertEquals(24L, result.getId());
        assertEquals(17L, result.getProjectId());
        assertEquals("T-9", result.getTaskCode());
        assertEquals("TODO", result.getStatus());
        verify(codes).nextTaskCode();
    }

    @Test
    void runCreateGeneratesCodeAndPreservesTaskAssociation() {
        when(codes.nextRunCode()).thenReturn("R-14");
        var result = runService.create(24L, runRequest());
        assertEquals(99L, result.getId());
        assertEquals(24L, result.getTaskId());
        assertEquals("R-14", result.getRunCode());
        assertEquals("PENDING", result.getStatus());
        verify(codes).nextRunCode();
    }

    @Test
    void projectUpdatePreservesCodeAndDoesNotAllocateAnother() {
        when(projects.updateById(any(ResearchProject.class))).thenReturn(1);
        UpdateProjectRequest request = new UpdateProjectRequest(); request.setName("新名称");
        assertEquals("P-6", projectService.update(17L, request).getProjectCode());
        var captor = ArgumentCaptor.forClass(ResearchProject.class);
        verify(projects).updateById(captor.capture());
        assertNull(captor.getValue().getProjectCode());
        verifyNoInteractions(codes);
    }

    @Test
    void taskUpdatePreservesCodeAndDoesNotAllocateAnother() {
        when(tasks.updateById(any(ExperimentTask.class))).thenReturn(1);
        UpdateTaskRequest request = new UpdateTaskRequest(); request.setName("新名称");
        assertEquals("T-8", taskService.update(24L, request).getTaskCode());
        var captor = ArgumentCaptor.forClass(ExperimentTask.class);
        verify(tasks).updateById(captor.capture());
        assertNull(captor.getValue().getTaskCode());
        verifyNoInteractions(codes);
    }

    @Test
    void runUpdatePreservesCodeAndDoesNotIncludeCodeInSqlSet() {
        when(runs.update(isNull(), any(Wrapper.class))).thenReturn(1);
        UpdateRunRequest request = new UpdateRunRequest(); request.setRunName("新名称");
        assertEquals("R-13", runService.update(99L, request).getRunCode());
        var captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(runs).update(isNull(), captor.capture());
        assertFalse(captor.getValue().getSqlSet().contains("run_code"));
        verifyNoInteractions(codes);
    }

    @Test
    void deletionDoesNotRewindProjectSequence() {
        when(codes.nextProjectCode()).thenReturn("P-7", "P-8");
        when(projects.deleteById(17L)).thenReturn(1);
        assertEquals("P-7", projectService.create(projectRequest()).getProjectCode());
        projectService.delete(17L);
        assertEquals("P-8", projectService.create(projectRequest()).getProjectCode());
        verify(codes, times(2)).nextProjectCode();
        verifyNoMoreInteractions(codes);
    }

    @Test
    void deletionDoesNotRewindTaskSequence() {
        when(codes.nextTaskCode()).thenReturn("T-9", "T-10");
        when(tasks.deleteById(24L)).thenReturn(1);
        assertEquals("T-9", taskService.create(17L, taskRequest()).getTaskCode());
        taskService.delete(24L);
        assertEquals("T-10", taskService.create(17L, taskRequest()).getTaskCode());
        verify(codes, times(2)).nextTaskCode();
        verifyNoMoreInteractions(codes);
    }

    @Test
    void deletionDoesNotRewindRunSequence() {
        when(codes.nextRunCode()).thenReturn("R-14", "R-15");
        when(runs.deleteById(99L)).thenReturn(1);
        assertEquals("R-14", runService.create(24L, runRequest()).getRunCode());
        runService.delete(99L);
        assertEquals("R-15", runService.create(24L, runRequest()).getRunCode());
        verify(codes, times(2)).nextRunCode();
        verifyNoMoreInteractions(codes);
    }

    @Test
    void invalidParentsFailBeforeCodeAllocation() {
        assertThrows(com.djh.researchops.exception.BusinessException.class, () -> taskService.create(99999L, taskRequest()));
        assertThrows(com.djh.researchops.exception.BusinessException.class, () -> runService.create(99999L, runRequest()));
        verifyNoInteractions(codes);
    }

    @Test
    void insertFailureDoesNotRetryOrRecycleAllocatedCode() {
        when(codes.nextProjectCode()).thenReturn("P-7");
        when(projects.insert(any(ResearchProject.class))).thenThrow(new IllegalStateException("insert failed"));
        assertThrows(IllegalStateException.class, () -> projectService.create(projectRequest()));
        verify(codes).nextProjectCode();
        verifyNoMoreInteractions(codes);
    }

    @Test
    void originalRestUrlsReturnBusinessCodesAndInternalIdsTogether() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ResearchProjectController(projectService),
                new ExperimentTaskController(taskService), new ExperimentRunController(runService)).build();
        mvc.perform(get("/api/projects/17")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(17)).andExpect(jsonPath("$.data.projectCode").value("P-6"));
        mvc.perform(get("/api/tasks/24")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(24)).andExpect(jsonPath("$.data.taskCode").value("T-8"));
        mvc.perform(get("/api/runs/99")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99)).andExpect(jsonPath("$.data.runCode").value("R-13"));
    }

    @Test
    void listVosIncludeBusinessCodes() {
        when(projects.selectList(isNull())).thenReturn(List.of(project));
        when(tasks.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(runs.selectList(any(Wrapper.class))).thenReturn(List.of(run));
        assertEquals("P-6", projectService.getAll().get(0).getProjectCode());
        assertEquals("T-8", taskService.getByProjectId(17L).get(0).getTaskCode());
        assertEquals("R-13", runService.getByTaskId(24L).get(0).getRunCode());
    }

    @ParameterizedTest
    @MethodSource("dtos")
    void createAndUpdateDtosCannotBindUserSpecifiedCodes(Class<?> dto) {
        assertTrue(Arrays.stream(dto.getDeclaredFields()).noneMatch(field -> field.getName().endsWith("Code")));
        // 与忽略未知 JSON 字段的 REST 绑定兼容：输入 Code 不会进入 DTO 或后续持久化。
        var mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        Object request = mapper.readValue("{\"projectCode\":\"P-999\",\"taskCode\":\"T-999\",\"runCode\":\"R-999\"}", dto);
        var bound = mapper.valueToTree(request);
        assertFalse(bound.has("projectCode"));
        assertFalse(bound.has("taskCode"));
        assertFalse(bound.has("runCode"));
    }

    @ParameterizedTest
    @MethodSource("codeFields")
    void entityUpdateStrategyPreventsCodeModification(java.lang.reflect.Field field) {
        assertEquals(FieldStrategy.NEVER, field.getAnnotation(TableField.class).updateStrategy());
    }

    static Stream<Class<?>> dtos() {
        return Stream.of(CreateProjectRequest.class, UpdateProjectRequest.class, CreateTaskRequest.class,
                UpdateTaskRequest.class, CreateRunRequest.class, UpdateRunRequest.class);
    }

    static Stream<java.lang.reflect.Field> codeFields() throws Exception {
        return Stream.of(ResearchProject.class.getDeclaredField("projectCode"),
                ExperimentTask.class.getDeclaredField("taskCode"), ExperimentRun.class.getDeclaredField("runCode"));
    }

    private CreateProjectRequest projectRequest() {
        var request = new CreateProjectRequest(); request.setName("新项目"); return request;
    }

    private CreateTaskRequest taskRequest() {
        var request = new CreateTaskRequest(); request.setName("新任务"); return request;
    }

    private CreateRunRequest runRequest() {
        var request = new CreateRunRequest(); request.setRunName("新运行"); return request;
    }
}
