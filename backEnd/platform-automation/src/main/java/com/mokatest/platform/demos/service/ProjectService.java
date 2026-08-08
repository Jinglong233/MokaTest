package com.mokatest.platform.demos.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.Project;

import java.util.List;

/**
* @author: JingLong
* @description 针对表【project】的数据库操作Service
* @createDate 2025-09-13 11:42:28
*/
public interface ProjectService extends IService<Project> {

    List<Project> allProject();

    Boolean addProject(Project project);

    Boolean updateProject(Project project);

    SaResult getProjectListByTeamId(Integer teamId);

    SaResult getProjectById(Integer id);

    Boolean deleteProject(Integer projectId);

    boolean canDeleteProject(Integer projectId, String loginId);
}
