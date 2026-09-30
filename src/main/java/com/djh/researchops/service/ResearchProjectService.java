package com.djh.researchops.service;

import com.djh.researchops.dto.CreateProjectRequest;
import com.djh.researchops.entity.ResearchProject;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.mapper.ResearchProjectMapper;
import com.djh.researchops.vo.ResearchProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResearchProjectService {

    private final ResearchProjectMapper researchProjectMapper;

    public ResearchProjectVO create(CreateProjectRequest request) {

        ResearchProject project = new ResearchProject();

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

    private ResearchProjectVO toVO(ResearchProject project) {

        ResearchProjectVO vo = new ResearchProjectVO();

        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setStatus(project.getStatus());
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());

        return vo;
    }
}