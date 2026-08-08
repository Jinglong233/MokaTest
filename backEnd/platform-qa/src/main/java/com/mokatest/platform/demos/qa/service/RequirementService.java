package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.Requirement;

import java.util.List;

/**
 * 需求池 Service
 */
public interface RequirementService extends IService<Requirement> {

    SaResult listByProject(Integer projectId, String keyword, String status, Integer moduleId, String reqType, String source, Integer page, Integer pageSize);

    SaResult saveOrUpdateRequirement(Requirement requirement);

    SaResult deleteRequirement(Integer id);

    SaResult batchDeleteRequirement(List<Integer> ids);

    SaResult getDetail(Integer id);

    SaResult transitionStatus(Integer requirementId, String targetStatus);

    SaResult getTraceability(Integer requirementId);

    /** 需求统计（全项目口径，供顶部统计卡片） */
    SaResult stats(Integer projectId);
}
