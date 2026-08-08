package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.Bug;

import java.util.List;

/**
 * BUG池 Service
 */
public interface BugService extends IService<Bug> {

    SaResult listByProject(Integer projectId, String keyword, String status, String severity, String priority, Integer requirementId, Integer testCaseId, Integer moduleId, String environment, String reproduceRate, String closeReason, Integer page, Integer pageSize);

    SaResult saveOrUpdateBug(Bug bug);

    SaResult deleteBug(Integer id);

    SaResult batchDeleteBug(List<Integer> ids);

    SaResult getDetail(Integer id);

    SaResult transitionStatus(Integer bugId, String targetStatus);

    /** BUG 统计（全项目口径，供顶部统计卡片） */
    SaResult stats(Integer projectId);
}
