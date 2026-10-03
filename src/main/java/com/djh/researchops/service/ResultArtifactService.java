package com.djh.researchops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.djh.researchops.dto.CreateArtifactRequest;
import com.djh.researchops.dto.UpdateArtifactRequest;
import com.djh.researchops.entity.ResultArtifact;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ExperimentRunMapper;
import com.djh.researchops.mapper.ResultArtifactMapper;
import com.djh.researchops.vo.ResultArtifactVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResultArtifactService {

    private static final List<String> ARTIFACT_TYPES =
            List.of("IMAGE", "MODEL", "POINT_CLOUD", "CHECKPOINT", "REPORT", "OTHER");

    private final ResultArtifactMapper resultArtifactMapper;

    private final ExperimentRunMapper experimentRunMapper;

    public ResultArtifactVO create(Long runId, CreateArtifactRequest request) {

        checkRunExists(runId);
        checkName(request.getArtifactName());
        checkType(request.getArtifactType());
        checkPath(request.getStoragePath());
        checkDescription(request.getDescription());
        checkFileSize(request.getFileSizeBytes());

        ResultArtifact artifact = new ResultArtifact();
        artifact.setRunId(runId);
        artifact.setArtifactName(request.getArtifactName());
        artifact.setArtifactType(request.getArtifactType());
        artifact.setStoragePath(request.getStoragePath());
        artifact.setDescription(request.getDescription());
        artifact.setFileSizeBytes(request.getFileSizeBytes());
        resultArtifactMapper.insert(artifact);

        // 重新读取数据库生成的 ID 和时间字段。
        return getById(artifact.getId());
    }

    public List<ResultArtifactVO> getByRunId(Long runId, String type) {

        checkRunExists(runId);
        if (type != null) checkType(type);

        LambdaQueryWrapper<ResultArtifact> query = new LambdaQueryWrapper<>();
        query.eq(ResultArtifact::getRunId, runId);
        if (type != null) query.eq(ResultArtifact::getArtifactType, type);
        query.orderByDesc(ResultArtifact::getId);

        List<ResultArtifactVO> result = new ArrayList<>();
        for (ResultArtifact artifact : resultArtifactMapper.selectList(query)) {
            result.add(toVO(artifact));
        }
        return result;
    }

    public ResultArtifactVO getById(Long artifactId) {

        return toVO(getArtifactOrThrow(artifactId));
    }

    public ResultArtifactVO update(Long artifactId, UpdateArtifactRequest request) {

        getArtifactOrThrow(artifactId);
        if (request.getArtifactName() == null && request.getArtifactType() == null
                && request.getStoragePath() == null && request.getDescription() == null
                && request.getFileSizeBytes() == null) {
            throw new BusinessException(400, "至少需要提供一个更新字段");
        }
        if (request.getArtifactName() != null) checkName(request.getArtifactName());
        if (request.getArtifactType() != null) checkType(request.getArtifactType());
        if (request.getStoragePath() != null) checkPath(request.getStoragePath());
        checkDescription(request.getDescription());
        checkFileSize(request.getFileSizeBytes());

        // 仅设置提供的可修改字段；时间字段仍由数据库维护。
        LambdaUpdateWrapper<ResultArtifact> update = new LambdaUpdateWrapper<>();
        update.eq(ResultArtifact::getId, artifactId);
        if (request.getArtifactName() != null) update.set(ResultArtifact::getArtifactName, request.getArtifactName());
        if (request.getArtifactType() != null) update.set(ResultArtifact::getArtifactType, request.getArtifactType());
        if (request.getStoragePath() != null) update.set(ResultArtifact::getStoragePath, request.getStoragePath());
        if (request.getDescription() != null) update.set(ResultArtifact::getDescription, request.getDescription());
        if (request.getFileSizeBytes() != null) update.set(ResultArtifact::getFileSizeBytes, request.getFileSizeBytes());
        if (resultArtifactMapper.update(null, update) == 0) {
            throw new BusinessException(404, "实验产物不存在");
        }
        return getById(artifactId);
    }

    public void delete(Long artifactId) {

        if (resultArtifactMapper.deleteById(artifactId) == 0) {
            throw new BusinessException(404, "实验产物不存在");
        }
    }

    private void checkRunExists(Long runId) {

        if (experimentRunMapper.selectById(runId) == null) {
            throw new BusinessException(404, "实验运行不存在");
        }
    }

    private ResultArtifact getArtifactOrThrow(Long artifactId) {

        ResultArtifact artifact = resultArtifactMapper.selectById(artifactId);
        if (artifact == null) throw new BusinessException(404, "实验产物不存在");
        return artifact;
    }

    private void checkName(String name) {

        if (name == null || name.isBlank()) throw new BusinessException(400, "实验产物名称不能为空");
        if (name.length() > 200) throw new BusinessException(400, "实验产物名称不能超过200个字符");
    }

    private void checkType(String type) {

        if (type == null || !ARTIFACT_TYPES.contains(type)) {
            throw new BusinessException(400, "实验产物类型不合法");
        }
    }

    private void checkPath(String path) {

        if (path == null || path.isBlank()) throw new BusinessException(400, "实验产物路径不能为空");
        if (path.length() > 1000) throw new BusinessException(400, "实验产物路径不能超过1000个字符");
    }

    private void checkDescription(String description) {

        if (description != null && description.length() > 500) {
            throw new BusinessException(400, "实验产物描述不能超过500个字符");
        }
    }

    private void checkFileSize(Long fileSizeBytes) {

        if (fileSizeBytes != null && fileSizeBytes < 0) {
            throw new BusinessException(400, "实验产物文件大小不能小于0");
        }
    }

    private ResultArtifactVO toVO(ResultArtifact artifact) {

        ResultArtifactVO vo = new ResultArtifactVO();
        vo.setId(artifact.getId());
        vo.setRunId(artifact.getRunId());
        vo.setArtifactName(artifact.getArtifactName());
        vo.setArtifactType(artifact.getArtifactType());
        vo.setStoragePath(artifact.getStoragePath());
        vo.setDescription(artifact.getDescription());
        vo.setFileSizeBytes(artifact.getFileSizeBytes());
        vo.setCreatedAt(artifact.getCreatedAt());
        vo.setUpdatedAt(artifact.getUpdatedAt());
        return vo;
    }
}
