package com.djh.researchops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.djh.researchops.dto.CreateLogRequest;
import com.djh.researchops.entity.ExperimentLog;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ExperimentLogMapper;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.vo.ExperimentLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperimentLogService {

    private final ExperimentLogMapper experimentLogMapper;

    private final ExperimentRunMapper experimentRunMapper;

    public ExperimentLogVO create(Long runId, CreateLogRequest request) {

        checkRunExists(runId);
        checkLevel(request.getLevel());

        ExperimentLog log = new ExperimentLog();
        log.setRunId(runId);
        log.setLevel(request.getLevel() != null ? request.getLevel() : "INFO");
        log.setContent(request.getContent());

        experimentLogMapper.insert(log);

        // 重新查询，获得数据库自动生成的创建时间。
        ExperimentLog savedLog = experimentLogMapper.selectById(log.getId());
        return toVO(savedLog);
    }

    public List<ExperimentLogVO> getByRunId(Long runId, String level) {

        checkRunExists(runId);
        checkLevel(level);

        LambdaQueryWrapper<ExperimentLog> query = new LambdaQueryWrapper<>();
        query.eq(ExperimentLog::getRunId, runId);
        if (level != null) {
            query.eq(ExperimentLog::getLevel, level);
        }
        query.orderByDesc(ExperimentLog::getId);

        List<ExperimentLog> logs = experimentLogMapper.selectList(query);
        List<ExperimentLogVO> result = new ArrayList<>();

        for (ExperimentLog log : logs) {
            result.add(toVO(log));
        }

        return result;
    }

    private void checkRunExists(Long runId) {

        if (experimentRunMapper.selectById(runId) == null) {
            throw new BusinessException(404, "实验运行不存在");
        }
    }

    private void checkLevel(String level) {

        // null 表示未提供；空字符串、空格、大小写不匹配均为非法值。
        if (level != null && !List.of("INFO", "WARN", "ERROR").contains(level)) {
            throw new BusinessException(400, "实验日志级别不合法");
        }
    }

    private ExperimentLogVO toVO(ExperimentLog log) {

        ExperimentLogVO vo = new ExperimentLogVO();
        vo.setId(log.getId());
        vo.setRunId(log.getRunId());
        vo.setLevel(log.getLevel());
        vo.setContent(log.getContent());
        vo.setCreatedAt(log.getCreatedAt());

        return vo;
    }
}
