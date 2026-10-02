package com.djh.researchops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.djh.researchops.dto.CreateRunRequest;
import com.djh.researchops.dto.UpdateRunRequest;
import com.djh.researchops.entity.ExperimentRun;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.mapper.ExperimentTaskMapper;
import com.djh.researchops.vo.ExperimentRunVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperimentRunService {

    private final ExperimentRunMapper experimentRunMapper;

    private final ExperimentTaskMapper experimentTaskMapper;

    public ExperimentRunVO create(Long taskId, CreateRunRequest request) {

        checkTaskExists(taskId);

        ExperimentRun run = new ExperimentRun();
        run.setTaskId(taskId);
        run.setRunName(request.getRunName());
        run.setStatus("PENDING");

        experimentRunMapper.insert(run);

        // 重新查询，获得数据库自动生成的时间字段。
        return getById(run.getId());
    }

    public List<ExperimentRunVO> getByTaskId(Long taskId) {

        checkTaskExists(taskId);

        LambdaQueryWrapper<ExperimentRun> query = new LambdaQueryWrapper<>();
        query.eq(ExperimentRun::getTaskId, taskId)
                .orderByDesc(ExperimentRun::getId);

        List<ExperimentRun> runs = experimentRunMapper.selectList(query);
        List<ExperimentRunVO> result = new ArrayList<>();

        for (ExperimentRun run : runs) {
            result.add(toVO(run));
        }

        return result;
    }

    public ExperimentRunVO getById(Long id) {

        return toVO(getRunOrThrow(id));
    }

    public ExperimentRunVO update(Long id, UpdateRunRequest request) {

        if (request.getRunName() == null
                && request.getStatus() == null
                && request.getErrorMessage() == null) {
            throw new BusinessException(400, "至少需要提供一个更新字段");
        }

        String status = request.getStatus();
        if (status != null
                && !List.of("PENDING", "RUNNING", "COMPLETED", "FAILED").contains(status)) {
            throw new BusinessException(400, "实验运行状态不合法");
        }

        ExperimentRun run = getRunOrThrow(id);

        // 只更新提供的字段，created_at、updated_at 仍由数据库维护。
        // Wrapper.set 可以显式写入 null，避免实体更新时 null 被忽略。
        LambdaUpdateWrapper<ExperimentRun> update = new LambdaUpdateWrapper<>();
        update.eq(ExperimentRun::getId, id);
        if (request.getRunName() != null) {
            update.set(ExperimentRun::getRunName, request.getRunName());
        }

        if (status != null) {
            update.set(ExperimentRun::getStatus, status);
            LocalDateTime now = LocalDateTime.now();

            if ("RUNNING".equals(status)
                    || "COMPLETED".equals(status)
                    || "FAILED".equals(status)) {
                if (run.getStartedAt() == null) {
                    update.set(ExperimentRun::getStartedAt, now);
                }
            }

            if ("RUNNING".equals(status)) {
                update.set(ExperimentRun::getFinishedAt, null);
            } else if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                update.set(ExperimentRun::getFinishedAt, now);
            }
            // PENDING 不修改已有的开始和结束时间。
        }

        if ("COMPLETED".equals(status)) {
            update.set(ExperimentRun::getErrorMessage, null);
        } else if (request.getErrorMessage() != null) {
            update.set(ExperimentRun::getErrorMessage, request.getErrorMessage());
        }

        int updatedRows = experimentRunMapper.update(null, update);
        if (updatedRows == 0) {
            throw new BusinessException(404, "实验运行不存在");
        }

        return getById(id);
    }

    public void delete(Long id) {

        int deletedRows = experimentRunMapper.deleteById(id);
        if (deletedRows == 0) {
            throw new BusinessException(404, "实验运行不存在");
        }
    }

    private void checkTaskExists(Long taskId) {

        if (experimentTaskMapper.selectById(taskId) == null) {
            throw new BusinessException(404, "实验任务不存在");
        }
    }

    private ExperimentRun getRunOrThrow(Long id) {

        ExperimentRun run = experimentRunMapper.selectById(id);
        if (run == null) {
            throw new BusinessException(404, "实验运行不存在");
        }

        return run;
    }

    private ExperimentRunVO toVO(ExperimentRun run) {

        ExperimentRunVO vo = new ExperimentRunVO();
        vo.setId(run.getId());
        vo.setTaskId(run.getTaskId());
        vo.setRunName(run.getRunName());
        vo.setStatus(run.getStatus());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setCreatedAt(run.getCreatedAt());
        vo.setUpdatedAt(run.getUpdatedAt());

        return vo;
    }
}
