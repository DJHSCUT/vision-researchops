package com.djh.researchops;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.djh.researchops.dto.CreateRunRequest;
import com.djh.researchops.dto.UpdateRunRequest;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.entity.ExperimentTask;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.mapper.ExperimentTaskMapper;
import com.djh.researchops.service.ExperimentRunService;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 仅使用模拟 Mapper，不启动 Spring，也不连接数据库。
class ExperimentRunServiceTests {

    private ExperimentRunMapper runMapper;
    private ExperimentTaskMapper taskMapper;
    private ExperimentRunService service;
    private ExperimentRun run;
    private LambdaUpdateWrapper<ExperimentRun> update;

    @BeforeAll
    static void initializeMapping() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ExperimentRun.class
        );
    }

    @BeforeEach
    void setUp() {
        runMapper = mock(ExperimentRunMapper.class);
        taskMapper = mock(ExperimentTaskMapper.class);
        service = new ExperimentRunService(runMapper, taskMapper);
        run = new ExperimentRun();
        run.setId(1L);
        run.setTaskId(2L);
        run.setRunName("运行一");
        run.setStatus("PENDING");
        when(runMapper.selectById(1L)).thenReturn(run);
        when(runMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            update = invocation.getArgument(1);
            return 1;
        });
    }

    @Test
    void completedSetsBothTimesAndExplicitlyClearsError() {
        run.setStatus("FAILED");
        run.setErrorMessage("旧错误");
        UpdateRunRequest request = statusRequest("COMPLETED");
        LocalDateTime before = LocalDateTime.now();

        service.update(1L, request);

        LocalDateTime started = (LocalDateTime) assignedValue("started_at");
        assertFalse(started.isBefore(before));
        assertFalse(started.isAfter(LocalDateTime.now()));
        assertEquals(started, assignedValue("finished_at"));
        assertNull(assignedValue("error_message"));
        assertFalse(update.getSqlSet().contains("created_at"));
        assertFalse(update.getSqlSet().contains("updated_at"));
    }

    @Test
    void runningPreservesStartAndClearsPreviousFinish() {
        run.setStatus("FAILED");
        run.setErrorMessage("旧错误");
        run.setStartedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 1, 13, 0));

        service.update(1L, statusRequest("RUNNING"));

        assertFalse(update.getSqlSet().contains("started_at"));
        assertNull(assignedValue("finished_at"));
        assertNull(assignedValue("error_message"));
    }

    @Test
    void firstRunningSetsStartTime() {
        service.update(1L, statusRequest("RUNNING"));

        assertInstanceOf(LocalDateTime.class, assignedValue("started_at"));
        assertNull(assignedValue("finished_at"));
    }

    @Test
    void failedCanStartDirectlyAndRecordError() {
        UpdateRunRequest request = statusRequest("FAILED");
        request.setErrorMessage("CUDA out of memory");

        service.update(1L, request);

        assertInstanceOf(LocalDateTime.class, assignedValue("started_at"));
        assertEquals(assignedValue("started_at"), assignedValue("finished_at"));
        assertEquals("CUDA out of memory", assignedValue("error_message"));
    }

    @Test
    void failedPreservesExistingStartAndOmittedError() {
        run.setStartedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        run.setErrorMessage("旧错误");

        service.update(1L, statusRequest("FAILED"));

        assertFalse(update.getSqlSet().contains("started_at"));
        assertInstanceOf(LocalDateTime.class, assignedValue("finished_at"));
        assertFalse(update.getSqlSet().contains("error_message"));
    }

    @Test
    void pendingPreservesTimes() {
        run.setStatus("FAILED");
        run.setErrorMessage("旧错误");
        run.setStartedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 1, 13, 0));

        service.update(1L, statusRequest("PENDING"));

        assertEquals("PENDING", assignedValue("status"));
        assertFalse(update.getSqlSet().contains("started_at"));
        assertFalse(update.getSqlSet().contains("finished_at"));
        assertNull(assignedValue("error_message"));
    }

    @Test
    void errorOnlyPatchLeavesStatusAndTimesUntouched() {
        run.setStatus("FAILED");
        UpdateRunRequest request = new UpdateRunRequest();
        request.setErrorMessage("修订失败原因");

        service.update(1L, request);

        assertEquals("修订失败原因", assignedValue("error_message"));
        assertFalse(update.getSqlSet().contains("status"));
        assertFalse(update.getSqlSet().contains("started_at"));
        assertFalse(update.getSqlSet().contains("finished_at"));
        assertFalse(update.getSqlSet().contains("run_name"));
    }

    @Test
    void nonFailedRunsRejectErrorOnlyPatch() {
        for (String status : new String[]{"PENDING", "RUNNING", "COMPLETED"}) {
            run.setStatus(status);
            UpdateRunRequest request = new UpdateRunRequest();
            request.setErrorMessage("CUDA out of memory");

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.update(1L, request));

            assertEquals(400, error.getCode());
            assertEquals("仅失败状态可填写失败原因", error.getMessage());
        }
        verify(runMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void nonFailedTargetStatusRejectsSimultaneousError() {
        run.setStatus("FAILED");
        run.setErrorMessage("旧错误");
        for (String status : new String[]{"PENDING", "RUNNING", "COMPLETED"}) {
            for (String message : new String[]{"CUDA out of memory", ""}) {
                UpdateRunRequest request = statusRequest(status);
                request.setErrorMessage(message);

                BusinessException error = assertThrows(BusinessException.class,
                        () -> service.update(1L, request));

                assertEquals(400, error.getCode());
                assertEquals("仅失败状态可填写失败原因", error.getMessage());
            }
        }
        verify(runMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void failedRunNormalizesBlankErrorToNull() {
        run.setStatus("FAILED");
        run.setErrorMessage("旧错误");
        for (String message : new String[]{"", " \n "}) {
            UpdateRunRequest request = new UpdateRunRequest();
            request.setErrorMessage(message);

            service.update(1L, request);

            assertNull(assignedValue("error_message"));
            assertFalse(update.getSqlSet().contains("status"));
        }
    }

    @Test
    void nameOnlyPatchCleansHistoricalNonFailedError() {
        run.setStatus("COMPLETED");
        run.setErrorMessage("历史脏数据");
        UpdateRunRequest request = new UpdateRunRequest();
        request.setRunName("修订名称");

        service.update(1L, request);

        assertEquals("修订名称", assignedValue("run_name"));
        assertNull(assignedValue("error_message"));
        assertFalse(update.getSqlSet().contains("status"));
        assertFalse(update.getSqlSet().contains("started_at"));
        assertFalse(update.getSqlSet().contains("finished_at"));
    }

    @Test
    void emptyAndInvalidStatusAreRejectedBeforeDatabaseAccess() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.update(1L, new UpdateRunRequest()));
        assertEquals(400, empty.getCode());
        assertEquals("至少需要提供一个更新字段", empty.getMessage());

        for (String status : new String[]{"", "running", "UNKNOWN", " RUNNING "}) {
            BusinessException invalid = assertThrows(BusinessException.class,
                    () -> service.update(1L, statusRequest(status)));
            assertEquals(400, invalid.getCode());
            assertEquals("实验运行状态不合法", invalid.getMessage());
        }
        verifyNoInteractions(runMapper);
    }

    @Test
    void missingTaskAndRunReturn404() {
        CreateRunRequest request = new CreateRunRequest();
        request.setRunName("运行一");
        BusinessException missingTask = assertThrows(BusinessException.class,
                () -> service.create(2L, request));
        assertEquals(404, missingTask.getCode());
        assertEquals("实验任务不存在", missingTask.getMessage());

        BusinessException missingRun = assertThrows(BusinessException.class,
                () -> service.update(99L, statusRequest("RUNNING")));
        assertEquals(404, missingRun.getCode());
        assertEquals("实验运行不存在", missingRun.getMessage());
        verify(runMapper, never()).insert(any(ExperimentRun.class));
        verify(runMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void requestValidationChecksNamesAndErrorLength() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            CreateRunRequest create = new CreateRunRequest();
            assertFalse(validator.validate(create).isEmpty());
            create.setRunName("   ");
            assertFalse(validator.validate(create).isEmpty());
            create.setRunName("名".repeat(150));
            assertTrue(validator.validate(create).isEmpty());
            create.setRunName("名".repeat(151));
            assertFalse(validator.validate(create).isEmpty());

            UpdateRunRequest patch = new UpdateRunRequest();
            assertTrue(validator.validate(patch).isEmpty());
            patch.setRunName("");
            assertFalse(validator.validate(patch).isEmpty());
            patch.setRunName(" \n ");
            assertFalse(validator.validate(patch).isEmpty());
            patch.setRunName(null);
            patch.setErrorMessage("错".repeat(1000));
            assertTrue(validator.validate(patch).isEmpty());
            patch.setErrorMessage("错".repeat(1001));
            assertFalse(validator.validate(patch).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING", "COMPLETED", "FAILED"})
    void taskQueryFiltersByStatusAndPreservesDescendingIdOrder(String status) {
        when(taskMapper.selectById(2L)).thenReturn(new ExperimentTask());
        run.setStatus(status);
        run.setStartedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 2, 5, 6));
        run.setErrorMessage("测试信息");
        when(runMapper.selectList(any(Wrapper.class))).thenReturn(List.of(run));

        var result = service.getByTaskId(2L, status);

        var query = capturedQuery();
        assertTrue(query.getSqlSegment().contains("task_id ="));
        assertTrue(query.getSqlSegment().contains("status ="));
        assertTrue(query.getSqlSegment().contains("ORDER BY id DESC"));
        assertEquals(2, query.getParamNameValuePairs().size());
        assertTrue(query.getParamNameValuePairs().containsValue(2L));
        assertTrue(query.getParamNameValuePairs().containsValue(status));
        assertEquals(status, result.get(0).getStatus());
        assertEquals(run.getTaskId(), result.get(0).getTaskId());
        assertEquals(run.getStartedAt(), result.get(0).getStartedAt());
        assertEquals(run.getFinishedAt(), result.get(0).getFinishedAt());
        assertEquals(run.getErrorMessage(), result.get(0).getErrorMessage());
        verify(taskMapper).selectById(2L);
    }

    @Test
    void legacyTaskQueryStillReturnsAllRunsInServiceOrder() {
        when(taskMapper.selectById(2L)).thenReturn(new ExperimentTask());
        ExperimentRun newer = new ExperimentRun();
        newer.setId(9L);
        newer.setTaskId(2L);
        newer.setStatus("FAILED");
        when(runMapper.selectList(any(Wrapper.class))).thenReturn(List.of(newer, run));

        var result = service.getByTaskId(2L);

        assertEquals(List.of(9L, 1L), result.stream().map(com.djh.researchops.vo.ExperimentRunVO::getId).toList());
        var query = capturedQuery();
        assertFalse(query.getSqlSegment().contains("status ="));
        assertTrue(query.getSqlSegment().contains("ORDER BY id DESC"));
        assertEquals(List.of(2L), List.copyOf(query.getParamNameValuePairs().values()));
    }

    @Test
    void taskQueryWithNullStatusDoesNotFilterStatus() {
        when(taskMapper.selectById(2L)).thenReturn(new ExperimentTask());
        when(runMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.getByTaskId(2L, null).isEmpty());
        var query = capturedQuery();
        assertFalse(query.getSqlSegment().contains("status ="));
        assertEquals(List.of(2L), List.copyOf(query.getParamNameValuePairs().values()));
    }

    @Test
    void taskQueryValidatesTaskBeforeQueryingRuns() {
        for (String status : new String[]{null, "FAILED"}) {
            BusinessException failure = assertThrows(BusinessException.class,
                    () -> service.getByTaskId(99999L, status));
            assertEquals(404, failure.getCode());
            assertEquals("实验任务不存在", failure.getMessage());
        }
        verifyNoInteractions(runMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "ERROR", "STOPPED", "", "failed", " FAILED "})
    void taskQueryRejectsInvalidStatusBeforeRunQuery(String status) {
        when(taskMapper.selectById(2L)).thenReturn(new ExperimentTask());
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.getByTaskId(2L, status));
        assertEquals(400, failure.getCode());
        assertEquals("实验运行状态不合法", failure.getMessage());
        verifyNoInteractions(runMapper);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<ExperimentRun> capturedQuery() {
        ArgumentCaptor<LambdaQueryWrapper<ExperimentRun>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(runMapper).selectList(captor.capture());
        return captor.getValue();
    }

    private UpdateRunRequest statusRequest(String status) {
        UpdateRunRequest request = new UpdateRunRequest();
        request.setStatus(status);
        return request;
    }

    // 检查实际生成的 SET 参数，包含 null，避免漏掉清空字段的 SQL。
    private Object assignedValue(String column) {
        Matcher matcher = Pattern.compile(
                column + "=\\#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}"
        ).matcher(update.getSqlSet());
        assertTrue(matcher.find(), "缺少字段更新：" + column);
        String parameter = matcher.group(1);
        assertTrue(update.getParamNameValuePairs().containsKey(parameter));
        return update.getParamNameValuePairs().get(parameter);
    }
}
