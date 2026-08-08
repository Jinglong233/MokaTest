package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.BugComment;

public interface BugCommentService extends IService<BugComment> {

    SaResult listByBug(Integer bugId);

    SaResult saveComment(BugComment comment, Integer createUserId);

    SaResult deleteComment(Integer id);
}
