package com.djh.researchops;

import com.djh.researchops.entity.ResearchProject;
import com.djh.researchops.mapper.ResearchProjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VisionResearchopsApplicationTests {

    @Autowired
    private ResearchProjectMapper researchProjectMapper;

    @Test
    void testResearchProjectMapper() {

        ResearchProject project = new ResearchProject();

        project.setName("Bitemporal 3DGS Local Change Detection");
        project.setDescription(
                "Research project for local change analysis based on bitemporal 3DGS"
        );
        project.setStatus("ACTIVE");

        researchProjectMapper.insert(project);

        System.out.println("插入后的项目ID：" + project.getId());

        ResearchProject result =
                researchProjectMapper.selectById(project.getId());

        System.out.println(result);
    }
}