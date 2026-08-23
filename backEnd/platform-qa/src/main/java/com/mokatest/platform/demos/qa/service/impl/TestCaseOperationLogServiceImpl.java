package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.qa.domain.TestCaseOperationLog;
import com.mokatest.platform.demos.qa.mapper.TestCaseOperationLogMapper;
import com.mokatest.platform.demos.qa.service.TestCaseOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用例操作日志 Service 实现
 */
@Service
@RequiredArgsConstructor
public class TestCaseOperationLogServiceImpl extends ServiceImpl<TestCaseOperationLogMapper, TestCaseOperationLog> implements TestCaseOperationLogService {

    private final UserMapper userMapper;

    @Override
    public SaResult listByCaseId(Integer caseId) {
        if (caseId == null) {
            return SaResult.error("缺少用例ID");
        }
        QueryWrapper<TestCaseOperationLog> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", caseId).orderByDesc("operate_time").orderByDesc("id");
        List<TestCaseOperationLog> list = baseMapper.selectList(wrapper);
        // operator_name 为空时按 operator_id 查用户昵称兜底填充
        Set<Integer> userIds = list.stream()
                .filter(l -> l.getOperatorName() == null || l.getOperatorName().isEmpty())
                .map(TestCaseOperationLog::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            Map<Integer, String> nameMap = users.stream()
                    .collect(Collectors.toMap(u -> u.getId().intValue(),
                            u -> u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername(),
                            (a, b) -> a));
            for (TestCaseOperationLog log : list) {
                if ((log.getOperatorName() == null || log.getOperatorName().isEmpty()) && log.getOperatorId() != null) {
                    log.setOperatorName(nameMap.getOrDefault(log.getOperatorId(), String.valueOf(log.getOperatorId())));
                }
            }
        }
        return SaResult.ok().setData(list);
    }

    @Override
    public void log(Integer caseId, String operation, String fieldName, String oldValue, String newValue, String comment) {
        doLog(caseId, null, operation, fieldName, oldValue, newValue, comment);
    }

    @Override
    public void logReviewVote(Integer caseId, Integer reviewId, String oldConclusion, String newConclusion, String comment) {
        doLog(caseId, reviewId, "REVIEW_VOTE", "conclusion", oldConclusion, newConclusion, comment);
    }

    private void doLog(Integer caseId, Integer reviewId, String operation, String fieldName, String oldValue, String newValue, String comment) {
        if (caseId == null || operation == null) {
            return;
        }
        TestCaseOperationLog log = new TestCaseOperationLog();
        log.setTestCaseId(caseId);
        log.setReviewId(reviewId);
        log.setOperation(operation);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setComment(comment);
        Integer loginId = null;
        try {
            loginId = StpUtil.getLoginIdAsInt();
        } catch (Exception ignored) {
            // 无登录上下文（如异步线程）时留空
        }
        log.setOperatorId(loginId);
        if (loginId != null) {
            User user = userMapper.selectById(loginId);
            if (user != null) {
                log.setOperatorName(user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername());
            }
        }
        log.setOperateTime(new Date());
        save(log);
    }
}
