package com.djh.researchops.service;

import com.djh.researchops.mapper.BusinessCodeSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class BusinessCodeService {

    private final BusinessCodeSequenceMapper sequenceMapper;

    public String nextProjectCode() {
        return nextCode("PROJECT", "P");
    }

    public String nextTaskCode() {
        return nextCode("TASK", "T");
    }

    public String nextRunCode() {
        return nextCode("RUN", "R");
    }

    private String nextCode(String entityType, String prefix) {
        // 三个 public 方法从创建 Service 经 Spring 代理进入独立事务；锁一直保留到递增提交。
        Long currentValue = sequenceMapper.lockCurrentValue(entityType);
        if (currentValue == null || currentValue < 0) {
            throw new IllegalStateException("业务编号序列未正确初始化：" + entityType);
        }
        long nextValue = Math.incrementExact(currentValue);
        if (sequenceMapper.advance(entityType, currentValue, nextValue) != 1) {
            throw new IllegalStateException("业务编号序列更新失败：" + entityType);
        }
        // 分配事务先提交，随后创建对象失败也不回收编号。
        return prefix + "-" + nextValue;
    }
}
