package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.domain.ui.UserRole;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.mapper.UserRoleMapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.TestCaseComment;
import com.mokatest.platform.demos.qa.mapper.TestCaseCommentMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.service.TestCaseCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TestCaseCommentServiceImpl extends ServiceImpl<TestCaseCommentMapper, TestCaseComment> implements TestCaseCommentService {

    private final UserMapper userMapper;
    private final TestCaseMapper testCaseMapper;
    private final MessageService messageService;
    private final ProjectMapper projectMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    public SaResult listByCase(Integer testCaseId) {
        QueryWrapper<TestCaseComment> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", testCaseId).orderByDesc("create_time");
        List<TestCaseComment> list = baseMapper.selectList(wrapper);
        // 批量补作者头像（评论表只冗余昵称，头像按 create_user_id 实时关联，换头像后历史评论也能跟随）
        Set<Integer> userIds = new HashSet<>();
        for (TestCaseComment c : list) {
            if (c.getCreateUserId() != null) {
                userIds.add(c.getCreateUserId());
            }
        }
        if (!userIds.isEmpty()) {
            Map<Integer, String> avatarMap = new HashMap<>();
            for (User u : userMapper.selectBatchIds(userIds)) {
                if (u.getId() != null && u.getAvatar() != null && !u.getAvatar().isEmpty()) {
                    avatarMap.put(u.getId().intValue(), u.getAvatar());
                }
            }
            for (TestCaseComment c : list) {
                if (c.getCreateUserId() != null) {
                    c.setCreateUserAvatar(avatarMap.get(c.getCreateUserId()));
                }
            }
        }
        return SaResult.ok().setData(list);
    }

    @Override
    public SaResult saveComment(TestCaseComment comment, Integer createUserId) {
        if (comment.getTestCaseId() == null) {
            return SaResult.error("缺少用例ID");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return SaResult.error("评论内容不能为空");
        }
        // 二级回复校验：父评论必须属于同一用例；只允许两级，回复的回复挂到顶层父评论下
        Integer parentId = comment.getParentId();
        if (parentId != null && parentId > 0) {
            TestCaseComment parent = getById(parentId);
            if (parent == null || !parent.getTestCaseId().equals(comment.getTestCaseId())) {
                return SaResult.error("被回复的评论不存在");
            }
            if (parent.getParentId() != null && parent.getParentId() > 0) {
                parentId = parent.getParentId();
            }
        } else {
            parentId = 0;
        }
        comment.setId(null);
        comment.setParentId(parentId);
        comment.setCreateUserId(createUserId);
        comment.setCreateUserName(getUserName(createUserId));
        comment.setCreateTime(new Date());
        boolean success = save(comment);

        // 发送 @ 提及 / 回复通知（失败不影响评论保存）
        if (success) {
            sendMentionMessage(comment, createUserId);
        }
        return success ? SaResult.ok("评论成功").setData(comment) : SaResult.error("评论失败");
    }

    /**
     * 发送评论 @ 提及 / 回复通知：
     * 1. mentionUserIds 中 @ 的用户（去重 + 仅项目成员可收，防越权通知）
     * 2. 回复评论时，被回复评论的作者也发通知（去重，仅项目成员可收）
     */
    private void sendMentionMessage(TestCaseComment comment, Integer createUserId) {
        try {
            // @提及列表可能为空（纯回复场景），不提前 return，保证回复通知仍能执行
            List<Integer> mentionIds = new ArrayList<>();
            if (comment.getMentionUserIds() != null && !comment.getMentionUserIds().trim().isEmpty()) {
                mentionIds = cn.hutool.json.JSONUtil.toList(
                        cn.hutool.json.JSONUtil.parseArray(comment.getMentionUserIds()), Integer.class);
            }
            TestCase testCase = testCaseMapper.selectById(comment.getTestCaseId());
            if (testCase == null) {
                return;
            }
            // 仅项目成员可收（项目级授权用户 ∪ 项目所属团队的团队管理员）
            Set<Integer> memberIds = loadProjectMemberIds(testCase.getProjectId());
            Map<String, Object> params = new HashMap<>();
            params.put("caseCode", testCase.getCaseCode());
            params.put("caseName", testCase.getCaseName());
            params.put("operatorName", getUserName(createUserId));
            params.put("commentContent", truncate(comment.getContent(), 80));
            params.put("projectName", getProjectName(testCase.getProjectId()));
            List<MessageContext> contexts = new ArrayList<>();
            Set<Integer> notified = new HashSet<>();
            List<Integer> targets = new ArrayList<>(new LinkedHashSet<>(mentionIds));
            // @提及通知
            for (Integer mentionId : targets) {
                if (mentionId != null && !mentionId.equals(createUserId) && memberIds.contains(mentionId)) {
                    notified.add(mentionId);
                    contexts.add(MessageContext.builder()
                            .eventType(MessageEventType.CASE_COMMENT_MENTION)
                            .senderId(createUserId)
                            .receiverId(mentionId)
                            .receiverRole("MENTION")
                            .projectId(testCase.getProjectId())
                            .teamId(getTeamIdByProjectId(testCase.getProjectId()))
                            .bizType("testCase")
                            .bizId(testCase.getId())
                            .params(params)
                            .commentId(comment.getId())
                            .build());
                }
            }
            // 回复通知：给被回复评论的作者发通知（使用独立的 CASE_COMMENT_REPLY eventType / 模板）
            if (comment.getParentId() != null && comment.getParentId() > 0) {
                TestCaseComment parent = getById(comment.getParentId());
                if (parent != null && parent.getCreateUserId() != null
                        && !parent.getCreateUserId().equals(createUserId)
                        && !notified.contains(parent.getCreateUserId())
                        && memberIds.contains(parent.getCreateUserId())) {
                    notified.add(parent.getCreateUserId());
                    contexts.add(MessageContext.builder()
                            .eventType(MessageEventType.CASE_COMMENT_REPLY)
                            .senderId(createUserId)
                            .receiverId(parent.getCreateUserId())
                            .receiverRole("MENTION")
                            .projectId(testCase.getProjectId())
                            .teamId(getTeamIdByProjectId(testCase.getProjectId()))
                            .bizType("testCase")
                            .bizId(testCase.getId())
                            .params(params)
                            .commentId(comment.getId())
                            .build());
                }
            }
            if (!contexts.isEmpty()) {
                messageService.sendBatch(contexts);
            }
        } catch (Exception e) {
            // 通知失败不影响评论保存，但留日志便于排查
            org.slf4j.LoggerFactory.getLogger(TestCaseCommentServiceImpl.class)
                    .error("用例评论 @/回复 通知发送失败, commentId={}", comment.getId(), e);
        }
    }

    /**
     * 获取项目成员ID集合（项目级授权用户 ∪ 项目所属团队的全体在职成员）。
     * 团队普通成员即可被 @——多数环境不会单独配置项目级授权（user_role 为空），
     * 若仅限团队管理员会导致可 @ 集合为空、通知全部被过滤。
     */
    private Set<Integer> loadProjectMemberIds(Integer projectId) {
        Set<Integer> memberIds = new HashSet<>();
        if (projectId == null) {
            return memberIds;
        }
        QueryWrapper<UserRole> roleWrapper = new QueryWrapper<>();
        roleWrapper.eq("scope_id", projectId).eq("status", 1);
        for (UserRole ur : userRoleMapper.selectList(roleWrapper)) {
            if (ur.getUserId() != null) {
                memberIds.add(ur.getUserId().intValue());
            }
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        if (project != null && project.getTeamId() != null) {
            QueryWrapper<TeamMember> tmWrapper = new QueryWrapper<>();
            tmWrapper.eq("team_id", project.getTeamId()).eq("status", 1);
            for (TeamMember tm : teamMemberMapper.selectList(tmWrapper)) {
                if (tm.getUserId() != null) {
                    memberIds.add(tm.getUserId().intValue());
                }
            }
        }
        return memberIds;
    }

    private String getProjectName(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        return project != null ? project.getProjectName() : null;
    }

    private Integer getTeamIdByProjectId(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        return project != null ? project.getTeamId() : null;
    }

    /**
     * 截断文本（评论摘要，防止通知内容过长）
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "…";
    }

    @Override
    public SaResult updateComment(Integer id, String content, Integer operatorId) {
        if (id == null) {
            return SaResult.error("缺少评论ID");
        }
        if (content == null || content.trim().isEmpty()) {
            return SaResult.error("评论内容不能为空");
        }
        TestCaseComment comment = getById(id);
        if (comment == null) {
            return SaResult.error("评论不存在");
        }
        // 仅本人可编辑
        if (comment.getCreateUserId() == null || !comment.getCreateUserId().equals(operatorId)) {
            return SaResult.error("只能编辑自己的评论");
        }
        TestCaseComment update = new TestCaseComment();
        update.setId(id);
        update.setContent(content.trim());
        boolean success = updateById(update);
        return success ? SaResult.ok("已更新") : SaResult.error("更新失败");
    }

    @Override
    public SaResult deleteComment(Integer id) {
        // 删除顶层评论时级联删除其所有回复
        QueryWrapper<TestCaseComment> replyWrapper = new QueryWrapper<>();
        replyWrapper.eq("parent_id", id);
        baseMapper.delete(replyWrapper);
        boolean success = removeById(id);
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }

    private String getUserName(Integer userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        return user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername();
    }
}
