package com.djh.researchops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.djh.researchops.dto.CreateMetricRequest;
import com.djh.researchops.dto.UpdateMetricRequest;
import com.djh.researchops.entity.ExperimentMetric;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ExperimentMetricMapper;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.vo.ExperimentMetricVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperimentMetricService {

    private final ExperimentMetricMapper experimentMetricMapper;

    private final ExperimentRunMapper experimentRunMapper;

    public ExperimentMetricVO create(Long runId, CreateMetricRequest request) {

        checkRunExists(runId);
        checkName(request.getMetricName());
        if (request.getMetricValue() == null) {
            throw new BusinessException(400, "实验指标数值不能为空");
        }
        if (request.getUnit() != null && request.getUnit().length() > 50) {
            throw new BusinessException(400, "实验指标单位不能超过50个字符");
        }
        if (request.getStep() != null && request.getStep() < 0) {
            throw new BusinessException(400, "实验指标步数不能小于0");
        }

        ExperimentMetric metric = new ExperimentMetric();
        metric.setRunId(runId);
        metric.setMetricName(request.getMetricName());
        metric.setMetricValue(request.getMetricValue());
        metric.setUnit(request.getUnit());
        metric.setStep(request.getStep());

        experimentMetricMapper.insert(metric);

        // 重新查询，获得数据库自动生成的创建时间。
        ExperimentMetric savedMetric = experimentMetricMapper.selectById(metric.getId());
        return toVO(savedMetric);
    }

    public List<ExperimentMetricVO> getByRunId(Long runId, String name) {

        checkRunExists(runId);

        LambdaQueryWrapper<ExperimentMetric> query = new LambdaQueryWrapper<>();
        query.eq(ExperimentMetric::getRunId, runId);
        if (name != null) {
            checkName(name);
            query.eq(ExperimentMetric::getMetricName, name)
                    // 由数据库排序，可空 step 不参与 Java 数值比较。
                    .orderByAsc(ExperimentMetric::getStep, ExperimentMetric::getId);
        } else {
            query.orderByDesc(ExperimentMetric::getId);
        }

        List<ExperimentMetric> metrics = experimentMetricMapper.selectList(query);
        List<ExperimentMetricVO> result = new ArrayList<>();
        for (ExperimentMetric metric : metrics) {
            result.add(toVO(metric));
        }
        return result;
    }

    public ExperimentMetricVO update(Long metricId, UpdateMetricRequest request) {

        getMetricOrThrow(metricId);
        if (request.getMetricName() == null && request.getMetricValue() == null
                && request.getUnit() == null && !request.isStepProvided()) {
            throw new BusinessException(400, "至少需要提供一个更新字段");
        }
        if (request.getMetricName() != null) checkName(request.getMetricName());
        if (request.getUnit() != null && request.getUnit().length() > 50) {
            throw new BusinessException(400, "实验指标单位不能超过50个字符");
        }
        if (request.getStep() != null && request.getStep() < 0) {
            throw new BusinessException(400, "实验指标步数不能小于0");
        }

        LambdaUpdateWrapper<ExperimentMetric> update = new LambdaUpdateWrapper<>();
        update.eq(ExperimentMetric::getId, metricId);
        if (request.getMetricName() != null) update.set(ExperimentMetric::getMetricName, request.getMetricName());
        if (request.getMetricValue() != null) update.set(ExperimentMetric::getMetricValue, request.getMetricValue());
        if (request.getUnit() != null) update.set(ExperimentMetric::getUnit, request.getUnit());
        if (request.isStepProvided()) update.set(ExperimentMetric::getStep, request.getStep());
        if (experimentMetricMapper.update(null, update) == 0) {
            throw new BusinessException(404, "实验指标不存在");
        }
        return toVO(getMetricOrThrow(metricId));
    }

    public void delete(Long metricId) {

        if (experimentMetricMapper.deleteById(metricId) == 0) {
            throw new BusinessException(404, "实验指标不存在");
        }
    }

    private ExperimentMetric getMetricOrThrow(Long metricId) {

        ExperimentMetric metric = experimentMetricMapper.selectById(metricId);
        if (metric == null) throw new BusinessException(404, "实验指标不存在");
        return metric;
    }

    private void checkRunExists(Long runId) {

        if (experimentRunMapper.selectById(runId) == null) {
            throw new BusinessException(404, "实验运行不存在");
        }
    }

    private void checkName(String name) {

        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "实验指标名称不能为空");
        }
        if (name.length() > 100) {
            throw new BusinessException(400, "实验指标名称不能超过100个字符");
        }
    }

    private ExperimentMetricVO toVO(ExperimentMetric metric) {

        ExperimentMetricVO vo = new ExperimentMetricVO();
        vo.setId(metric.getId());
        vo.setRunId(metric.getRunId());
        vo.setMetricName(metric.getMetricName());
        vo.setMetricValue(metric.getMetricValue());
        vo.setUnit(metric.getUnit());
        vo.setStep(metric.getStep());
        vo.setCreatedAt(metric.getCreatedAt());
        return vo;
    }
}
