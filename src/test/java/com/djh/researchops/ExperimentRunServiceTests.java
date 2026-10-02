package com.djh.researchops;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.djh.researchops.dto.CreateRunRequest;
import com.djh.researchops.dto.UpdateRunRequest;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.mapper.ExperimentTaskMapper;
import com.djh.researchops.service.ExperimentRunService;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
        run.setErrorMessage("旧错误");
        UpdateRunRequest request = statusRequest("COMPLETED");
        request.setErrorMessage("这条信息应被清空");
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
        run.setStartedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 1, 13, 0));

        service.update(1L, statusRequest("RUNNING"));

        assertFalse(update.getSqlSet().contains("started_at"));
        assertNull(assignedValue("finished_at"));
        assertFalse(update.getSqlSet().contains("error_message"));
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
        run.setStartedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 1, 13, 0));

        service.update(1L, statusRequest("PENDING"));

        assertEquals("PENDING", assignedValue("status"));
        assertFalse(update.getSqlSet().contains("started_at"));
        assertFalse(update.getSqlSet().contains("finished_at"));
    }

    @Test
    void errorOnlyPatchLeavesStatusAndTimesUntouched() {
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
