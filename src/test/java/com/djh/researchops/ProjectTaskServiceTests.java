package com.djh.researchops;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.djh.researchops.entity.*;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.*;
import com.djh.researchops.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTaskServiceTests {
    private ResearchProjectMapper projects;
    private ExperimentTaskMapper tasks;
    private ResearchProjectService projectService;
    private ExperimentTaskService taskService;

    @BeforeAll
    static void mappings() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "project-task-tests");
        assistant.setCurrentNamespace("project-task-tests");
        TableInfoHelper.initTableInfo(assistant, ResearchProject.class);
        TableInfoHelper.initTableInfo(assistant, ExperimentTask.class);
    }

    @BeforeEach
    void setUp() {
        projects = mock(ResearchProjectMapper.class); tasks = mock(ExperimentTaskMapper.class);
        var codes = mock(BusinessCodeService.class);
        projectService = new ResearchProjectService(projects, codes);
        taskService = new ExperimentTaskService(tasks, projects, codes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P-1", "p1"})
    void projectLookupUsesExactCodeColumn(String input) {
        var project = new ResearchProject(); project.setId(707L); project.setProjectCode("P-1");
        when(projects.selectOne(any())).thenReturn(project);
        var result = projectService.getByProjectCode(input);
        assertEquals(707L, result.getId()); assertEquals("P-1", result.getProjectCode());
        var capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(projects).selectOne(capture.capture());
        assertEquals("(project_code = #{ew.paramNameValuePairs.MPGENVAL1})", capture.getValue().getSqlSegment());
        assertTrue(capture.getValue().getParamNameValuePairs().containsValue("P-1"));
        verifyNoMoreInteractions(projects);
    }

    @Test
    void missingProjectUsesBusiness404() {
        var failure = assertThrows(BusinessException.class, () -> projectService.getByProjectCode("P-99999"));
        assertEquals(404, failure.getCode()); assertEquals("研究项目不存在", failure.getMessage());
    }

    @Test
    void invalidProjectDoesNotReachMapper() {
        assertEquals(400, assertThrows(BusinessException.class, () -> projectService.getByProjectCode("P-0")).getCode());
        verifyNoInteractions(projects);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TODO", "RUNNING", "COMPLETED", "FAILED"})
    void taskQueryIncludesProjectAndStatusWithStableOrder(String status) {
        when(projects.selectById(707L)).thenReturn(new ResearchProject());
        var task = new ExperimentTask(); task.setTaskCode("T-77"); task.setStatus(status);
        when(tasks.selectList(any())).thenReturn(List.of(task));
        assertEquals("T-77", taskService.getByProjectId(707L, status).get(0).getTaskCode());
        var capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tasks).selectList(capture.capture());
        var query = capture.getValue();
        assertEquals("(project_id = #{ew.paramNameValuePairs.MPGENVAL1} AND status = #{ew.paramNameValuePairs.MPGENVAL2}) ORDER BY id DESC", query.getSqlSegment());
        assertTrue(query.getParamNameValuePairs().containsValue(707L)); assertTrue(query.getParamNameValuePairs().containsValue(status));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nullStatusAndOriginalSignatureQueryAllTasks(boolean original) {
        when(projects.selectById(707L)).thenReturn(new ResearchProject()); when(tasks.selectList(any())).thenReturn(List.of());
        assertTrue((original ? taskService.getByProjectId(707L) : taskService.getByProjectId(707L, null)).isEmpty());
        var capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class); verify(tasks).selectList(capture.capture());
        assertEquals("(project_id = #{ew.paramNameValuePairs.MPGENVAL1}) ORDER BY id DESC", capture.getValue().getSqlSegment());
    }
}
