package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCaseComment;

public interface TestCaseCommentService extends IService<TestCaseComment> {

    SaResult listByCase(Integer testCaseId);

    SaResult saveComment(TestCaseComment comment, Integer createUserId);

    /** 编辑评论（仅本人可改，只允许改内容） */
    SaResult updateComment(Integer id, String content, Integer operatorId);

    SaResult deleteComment(Integer id);
}
