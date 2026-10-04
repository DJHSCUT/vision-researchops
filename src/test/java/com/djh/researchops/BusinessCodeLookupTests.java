package com.djh.researchops;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.entity.ExperimentTask;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.*;
import com.djh.researchops.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessCodeLookupTests {
    private ExperimentTaskMapper tasks;
    private ExperimentRunMapper runs;
    private ExperimentTaskService taskService;
    private ExperimentRunService runService;

    @BeforeAll
    static void mappings() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "code-lookup-tests");
        assistant.setCurrentNamespace("code-lookup-tests");
        TableInfoHelper.initTableInfo(assistant, ExperimentTask.class);
        TableInfoHelper.initTableInfo(assistant, ExperimentRun.class);
    }

    @BeforeEach
    void setUp() {
        tasks = mock(ExperimentTaskMapper.class);
        runs = mock(ExperimentRunMapper.class);
        var codes = mock(BusinessCodeService.class);
        taskService = new ExperimentTaskService(tasks, mock(ResearchProjectMapper.class), codes);
        runService = new ExperimentRunService(runs, tasks, codes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T-1", "t1"})
    void taskLookupUsesUniqueCodeColumnNotPrimaryKey(String input) {
        var task = new ExperimentTask(); task.setId(303L); task.setTaskCode("T-1");
        when(tasks.selectOne(any())).thenReturn(task);
        var result = taskService.getByTaskCode(input);
        assertEquals(303L, result.getId());
        assertEquals("T-1", result.getTaskCode());
        var capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tasks).selectOne(capture.capture());
        assertEquals("(task_code = #{ew.paramNameValuePairs.MPGENVAL1})", capture.getValue().getSqlSegment());
        assertTrue(capture.getValue().getParamNameValuePairs().containsValue("T-1"));
        verifyNoMoreInteractions(tasks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"R-2", "r2"})
    void runLookupUsesUniqueCodeColumnNotPrimaryKey(String input) {
        var run = new ExperimentRun(); run.setId(101L); run.setRunCode("R-2");
        when(runs.selectOne(any())).thenReturn(run);
        var result = runService.getByRunCode(input);
        assertEquals(101L, result.getId());
        assertEquals("R-2", result.getRunCode());
        var capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(runs).selectOne(capture.capture());
        assertEquals("(run_code = #{ew.paramNameValuePairs.MPGENVAL1})", capture.getValue().getSqlSegment());
        assertTrue(capture.getValue().getParamNameValuePairs().containsValue("R-2"));
        verifyNoMoreInteractions(runs);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missingCodeUsesExistingBusiness404Style(boolean task) {
        var failure = assertThrows(BusinessException.class,
                () -> { if (task) taskService.getByTaskCode("T-99999"); else runService.getByRunCode("R-99999"); });
        assertEquals(404, failure.getCode());
        assertEquals(task ? "实验任务不存在" : "实验运行不存在", failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void invalidCodeDoesNotQueryMapper(boolean task) {
        var failure = assertThrows(BusinessException.class,
                () -> { if (task) taskService.getByTaskCode("T-0"); else runService.getByRunCode("R--1"); });
        assertEquals(400, failure.getCode());
        verifyNoInteractions(tasks, runs);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unknownMapperFailurePropagates(boolean task) {
        var failure = new IllegalStateException("test-only lookup failure");
        if (task) when(tasks.selectOne(any())).thenThrow(failure);
        else when(runs.selectOne(any())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> { if (task) taskService.getByTaskCode("T-1"); else runService.getByRunCode("R-2"); }));
    }
}
