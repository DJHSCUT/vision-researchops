package com.djh.researchops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.djh.researchops.dto.CreateTaskRequest;
import com.djh.researchops.dto.UpdateTaskRequest;
import com.djh.researchops.entity.ExperimentTask;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ExperimentTaskMapper;
import com.djh.researchops.mapper.ResearchProjectMapper;
import com.djh.researchops.vo.ExperimentTaskVO;
import com.djh.researchops.util.BusinessCodeParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperimentTaskService {

    private final ExperimentTaskMapper experimentTaskMapper;

    private final ResearchProjectMapper researchProjectMapper;

    private final BusinessCodeService businessCodeService;

    public ExperimentTaskVO create(Long projectId, CreateTaskRequest request) {

        checkProjectExists(projectId);

        ExperimentTask task = new ExperimentTask();
        task.setTaskCode(businessCodeService.nextTaskCode());
        task.setProjectId(projectId);
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setStatus("TODO");

        experimentTaskMapper.insert(task);

        // 重新查询，获得数据库自动生成的时间字段。
        return getById(task.getId());
    }

    public List<ExperimentTaskVO> getByProjectId(Long projectId) {
        return getByProjectId(projectId, null);
    }

    public List<ExperimentTaskVO> getByProjectId(Long projectId, String status) {

        checkProjectExists(projectId);

        LambdaQueryWrapper<ExperimentTask> query = new LambdaQueryWrapper<>();
        query.eq(ExperimentTask::getProjectId, projectId)
                .eq(status != null, ExperimentTask::getStatus, status)
                .orderByDesc(ExperimentTask::getId);

        List<ExperimentTask> tasks = experimentTaskMapper.selectList(query);
        List<ExperimentTaskVO> result = new ArrayList<>();

        for (ExperimentTask task : tasks) {
            result.add(toVO(task));
        }

        return result;
    }

    public ExperimentTaskVO getById(Long id) {

        return toVO(getTaskOrThrow(id));
    }

    public ExperimentTaskVO getByTaskCode(String taskCode) {
        String normalized = BusinessCodeParser.normalizeTaskCode(taskCode);
        if (normalized == null) throw new BusinessException(400, "实验任务编号不合法");
        ExperimentTask task = experimentTaskMapper.selectOne(new LambdaQueryWrapper<ExperimentTask>()
                .eq(ExperimentTask::getTaskCode, normalized));
        if (task == null) throw new BusinessException(404, "实验任务不存在");
        return toVO(task);
    }

    public ExperimentTaskVO update(Long id, UpdateTaskRequest request) {

        if (request.getName() == null
                && request.getDescription() == null
                && request.getStatus() == null) {
            throw new BusinessException(400, "至少需要提供一个更新字段");
        }

        getTaskOrThrow(id);

        // 沿用现有 PATCH 语义：null 不更新，空字符串可以清空描述。
        // 仅更新传入的业务字段，让数据库自动维护时间字段。
        ExperimentTask updatedTask = new ExperimentTask();
        updatedTask.setId(id);
        updatedTask.setName(request.getName());
        updatedTask.setDescription(request.getDescription());
        updatedTask.setStatus(request.getStatus());

        int updatedRows = experimentTaskMapper.updateById(updatedTask);
        if (updatedRows == 0) {
            throw new BusinessException(404, "实验任务不存在");
        }

        return getById(id);
    }

    public void delete(Long id) {

        int deletedRows = experimentTaskMapper.deleteById(id);
        if (deletedRows == 0) {
            throw new BusinessException(404, "实验任务不存在");
        }
    }

    private void checkProjectExists(Long projectId) {

        if (researchProjectMapper.selectById(projectId) == null) {
            throw new BusinessException(404, "研究项目不存在");
        }
    }

    private ExperimentTask getTaskOrThrow(Long id) {

        ExperimentTask task = experimentTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        return task;
    }

    private ExperimentTaskVO toVO(ExperimentTask task) {

        ExperimentTaskVO vo = new ExperimentTaskVO();
        vo.setId(task.getId());
        vo.setTaskCode(task.getTaskCode());
        vo.setProjectId(task.getProjectId());
        vo.setName(task.getName());
        vo.setDescription(task.getDescription());
        vo.setStatus(task.getStatus());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());

        return vo;
    }
}
