package com.djh.researchops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.djh.researchops.util.BusinessCodeParser;

import com.djh.researchops.dto.CreateProjectRequest;
import com.djh.researchops.dto.UpdateProjectRequest;
import com.djh.researchops.entity.ResearchProject;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ResearchProjectMapper;
import com.djh.researchops.vo.ResearchProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResearchProjectService {

    private final ResearchProjectMapper researchProjectMapper;

    private final BusinessCodeService businessCodeService;

    public ResearchProjectVO getByProjectCode(String projectCode) {
        String normalized = BusinessCodeParser.normalizeProjectCode(projectCode);
        if (normalized == null) throw new BusinessException(400, "研究项目编号不合法");
        ResearchProject project = researchProjectMapper.selectOne(new LambdaQueryWrapper<ResearchProject>()
                .eq(ResearchProject::getProjectCode, normalized));
        if (project == null) throw new BusinessException(404, "研究项目不存在");
        return toVO(project);
    }

    public ResearchProjectVO create(CreateProjectRequest request) {

        ResearchProject project = new ResearchProject();

        project.setProjectCode(businessCodeService.nextProjectCode());

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setStatus("ACTIVE");

        researchProjectMapper.insert(project);

        // 数据库会自动生成 created_at、updated_at，
        // 所以重新查询一次，获得完整数据
        ResearchProject savedProject =
                researchProjectMapper.selectById(project.getId());

        return toVO(savedProject);
    }

    public ResearchProjectVO getById(Long id) {

        ResearchProject project =
                researchProjectMapper.selectById(id);

        if (project == null) {
            throw new BusinessException(
                    404,
                    "研究项目不存在"
            );
        }

        return toVO(project);
    }

    public List<ResearchProjectVO> getAll() {

        List<ResearchProject> projects =
                researchProjectMapper.selectList(null);
        List<ResearchProjectVO> result = new ArrayList<>();

        for (ResearchProject project : projects) {
            result.add(toVO(project));
        }

        return result;
    }

    public ResearchProjectVO update(Long id, UpdateProjectRequest request) {

        if (request.getName() == null
                && request.getDescription() == null
                && request.getStatus() == null) {
            throw new BusinessException(400, "至少需要提供一个更新字段");
        }

        ResearchProject project =
                researchProjectMapper.selectById(id);

        if (project == null) {
            throw new BusinessException(404, "研究项目不存在");
        }

        // 只更新业务字段，让数据库自动维护时间字段。
        ResearchProject updatedProject = new ResearchProject();
        updatedProject.setId(id);
        updatedProject.setName(request.getName() != null
                ? request.getName() : project.getName());
        updatedProject.setDescription(request.getDescription() != null
                ? request.getDescription() : project.getDescription());
        updatedProject.setStatus(request.getStatus() != null
                ? request.getStatus() : project.getStatus());

        int updatedRows = researchProjectMapper.updateById(updatedProject);
        if (updatedRows == 0) {
            throw new BusinessException(404, "研究项目不存在");
        }

        // 重新查询，返回数据库生成的最新更新时间。
        return getById(id);
    }

    public void delete(Long id) {

        int deletedRows = researchProjectMapper.deleteById(id);
        if (deletedRows == 0) {
            throw new BusinessException(404, "研究项目不存在");
        }
    }

    private ResearchProjectVO toVO(ResearchProject project) {

        ResearchProjectVO vo = new ResearchProjectVO();

        vo.setId(project.getId());
        vo.setProjectCode(project.getProjectCode());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setStatus(project.getStatus());
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());

        return vo;
    }
}
