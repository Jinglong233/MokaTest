package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.domain.ui.UserRole;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.mapper.UserRoleMapper;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.domain.BugComment;
import com.mokatest.platform.demos.qa.mapper.BugCommentMapper;
import com.mokatest.platform.demos.qa.mapper.BugMapper;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.service.BugCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BugCommentServiceImpl extends ServiceImpl<BugCommentMapper, BugComment> implements BugCommentService {

    private final MessageService messageService;
    private final BugMapper bugMapper;
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final com.mokatest.platform.demos.qa.mapper.QaModuleMapper qaModuleMapper;
    private final com.mokatest.platform.demos.qa.message.service.impl.NotifyDispatchService notifyDispatchService;

    @Override
    public SaResult listByBug(Integer bugId) {
        QueryWrapper<BugComment> wrapper = new QueryWrapper<>();
        wrapper.eq("bug_id", bugId).orderByDesc("create_time");
        List<BugComment> list = baseMapper.selectList(wrapper);
        // 批量补作者头像 + 昵称
        Set<Integer> userIds = new HashSet<>();
        for (BugComment c : list) {
            if (c.getCreateUserId() != null) userIds.add(c.getCreateUserId());
        }
        if (!userIds.isEmpty()) {
            Map<Integer, String> avatarMap = new HashMap<>();
            Map<Integer, String> nameMap = new HashMap<>();
            for (User u : userMapper.selectBatchIds(userIds)) {
                if (u.getId() != null) {
                    if (u.getAvatar() != null && !u.getAvatar().isEmpty()) {
                        avatarMap.put(u.getId().intValue(), u.getAvatar());
                    }
                    nameMap.put(u.getId().intValue(),
                        u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername());
                }
            }
            for (BugComment c : list) {
                if (c.getCreateUserId() != null) {
                    c.setCreateUserAvatar(avatarMap.get(c.getCreateUserId()));
                    if (c.getCreateUserName() == null || c.getCreateUserName().isEmpty()) {
                        c.setCreateUserName(nameMap.get(c.getCreateUserId()));
                    }
                }
            }
        }
        // 构建评论树
        List<BugComment> roots = new ArrayList<>();
        Map<Integer, List<BugComment>> replyMap = new HashMap<>();
        for (BugComment c : list) {
            if (c.getParentId() == null || c.getParentId() == 0) {
                roots.add(c);
            } else {
                replyMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (BugComment root : roots) {
            tree.add(buildCommentNode(root, replyMap));
        }
        return SaResult.ok().setData(tree);
    }

    private Map<String, Object> buildCommentNode(BugComment c, Map<Integer, List<BugComment>> replyMap) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", c.getId());
        node.put("bugId", c.getBugId());
        node.put("parentId", c.getParentId());
        node.put("content", c.getContent());
        node.put("mentionUserIds", c.getMentionUserIds());
        node.put("createUserId", c.getCreateUserId());
        node.put("createUserName", c.getCreateUserName());
        node.put("createUserAvatar", c.getCreateUserAvatar());
        node.put("createTime", c.getCreateTime());
        List<Map<String, Object>> replies = new ArrayList<>();
        for (BugComment r : replyMap.getOrDefault(c.getId(), Collections.emptyList())) {
            replies.add(buildCommentNode(r, replyMap));
        }
        node.put("replies", replies);
        return node;
    }

    @Override
    public SaResult saveComment(BugComment comment, Integer createUserId) {
        if (comment.getBugId() == null) {
            return SaResult.error("缺少BugID");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return SaResult.error("评论内容不能为空");
        }
        // 二级回复校验：父评论必须属于同一BUG；只允许两级，回复的回复挂到顶层父评论下
        Integer parentId = comment.getParentId();
        if (parentId != null && parentId > 0) {
            BugComment parent = getById(parentId);
            if (parent == null || !parent.getBugId().equals(comment.getBugId())) {
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
        comment.setCreateUserName(getOperatorName(createUserId));
        comment.setCreateTime(new Date());
        boolean success = save(comment);

        // 发送 @ 提及通知
        if (success) {
            sendMentionMessage(comment, createUserId);
        }

        return success ? SaResult.ok("评论成功").setData(comment) : SaResult.error("评论失败");
    }

    /**
     * 发送评论 @ 提及 / 回复通知
     */
    private void sendMentionMessage(BugComment comment, Integer createUserId) {
        try {
            // @提及列表可能为空（纯回复场景），不提前 return，保证回复通知仍能执行
            List<Integer> mentionIds = new ArrayList<>();
            if (comment.getMentionUserIds() != null && !comment.getMentionUserIds().trim().isEmpty()) {
                mentionIds = cn.hutool.json.JSONUtil.toList(
                        cn.hutool.json.JSONUtil.parseArray(comment.getMentionUserIds()), Integer.class);
            }
            Bug bug = bugMapper.selectById(comment.getBugId());
            if (bug == null) {
                return;
            }
            // 去重 + 仅保留项目成员（项目级授权用户 ∪ 项目所属团队成员），防止 @ 任意用户ID 造成越权通知
            Set<Integer> memberIds = loadProjectMemberIds(bug.getProjectId());
            List<Integer> targets = new ArrayList<>(new LinkedHashSet<>(mentionIds));
            Map<String, Object> params = new HashMap<>();
            params.put("bugCode", bug.getBugCode());
            params.put("bugTitle", bug.getTitle());
            params.put("operatorName", getOperatorName(createUserId));
            params.put("commentContent", truncate(comment.getContent(), 80));
            params.put("severity", bug.getSeverity());
            params.put("environment", bug.getEnvironment());
            params.put("deadline", bug.getDeadline() != null
                    ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(bug.getDeadline()) : null);
            params.put("foundVersion", bug.getFoundVersion());
            params.put("projectName", getProjectName(bug.getProjectId()));
            params.put("moduleName", getModuleName(bug.getModuleId()));
            Map<String, Object> bugSnapshot = buildBugSnapshot(bug);
            List<MessageContext> contexts = new ArrayList<>();
            Set<Integer> notified = new HashSet<>();
            for (Integer mentionId : targets) {
                if (mentionId != null && !mentionId.equals(createUserId) && memberIds.contains(mentionId)) {
                    notified.add(mentionId);
                    contexts.add(MessageContext.builder()
                            .eventType(MessageEventType.BUG_COMMENT_MENTION)
                            .senderId(createUserId)
                            .receiverId(mentionId)
                            .receiverRole("MENTION")
                            .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                            .bizType("bug")
                            .bizId(bug.getId())
                            .params(params)
                            .snapshot(bugSnapshot)
                            .commentId(comment.getId())
                            .build());
                }
            }
            // 回复通知：给被回复评论的作者发通知（使用独立的 BUG_COMMENT_REPLY eventType / 模板）
            if (comment.getParentId() != null && comment.getParentId() > 0) {
                BugComment parent = getById(comment.getParentId());
                if (parent != null && parent.getCreateUserId() != null
                        && !parent.getCreateUserId().equals(createUserId)
                        && !notified.contains(parent.getCreateUserId())
                        && memberIds.contains(parent.getCreateUserId())) {
                    notified.add(parent.getCreateUserId());
                    contexts.add(MessageContext.builder()
                            .eventType(MessageEventType.BUG_COMMENT_REPLY)
                            .senderId(createUserId)
                            .receiverId(parent.getCreateUserId())
                            .receiverRole("MENTION")
                            .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                            .bizType("bug")
                            .bizId(bug.getId())
                            .params(params)
                            .commentId(comment.getId())
                            .build());
                }
            }
            if (!contexts.isEmpty()) {
                messageService.sendBatch(contexts);
            }
        } catch (Exception e) {
            // 解析失败时忽略，不影响评论保存
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

    /**
     * 获取操作人名称
     */
    private String getOperatorName(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        return user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername();
    }

    /**
     * 获取项目名称（通知变量用）
     */
    private String getProjectName(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        return project != null ? project.getProjectName() : null;
    }

    /**
     * 获取模块名称（通知变量用）
     */
    private String getModuleName(Integer moduleId) {
        if (moduleId == null) {
            return null;
        }
        com.mokatest.platform.demos.qa.domain.QaModule module = qaModuleMapper.selectById(moduleId);
        return module != null ? module.getModuleName() : null;
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

    /**
     * 构建 BUG 快照（用于消息详情展示）
     */
    private Map<String, Object> buildBugSnapshot(Bug bug) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("bugCode", bug.getBugCode());
        snapshot.put("title", bug.getTitle());
        snapshot.put("description", bug.getDescription());
        snapshot.put("severity", bug.getSeverity());
        snapshot.put("priority", bug.getPriority());
        snapshot.put("status", bug.getStatus());
        snapshot.put("environment", bug.getEnvironment());
        snapshot.put("reproduceSteps", bug.getReproduceSteps());
        snapshot.put("foundVersion", bug.getFoundVersion());
        snapshot.put("fixedVersion", bug.getFixedVersion());
        snapshot.put("deadline", bug.getDeadline());
        snapshot.put("reproduceRate", bug.getReproduceRate());
        snapshot.put("closeReason", bug.getCloseReason());
        snapshot.put("moduleId", bug.getModuleId());
        snapshot.put("requirementId", bug.getRequirementId());
        snapshot.put("reporterId", bug.getReporterId());
        snapshot.put("assigneeId", bug.getAssigneeId());
        snapshot.put("tags", bug.getTags());
        fillProjectTeamInfo(snapshot, bug.getProjectId());
        return snapshot;
    }

    /**
     * 填充快照中的项目/团队名称
     */
    private void fillProjectTeamInfo(Map<String, Object> snapshot, Integer projectId) {
        if (projectId == null) {
            snapshot.put("projectName", null);
            snapshot.put("teamName", null);
            return;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        String projectName = project != null ? project.getProjectName() : null;
        String teamName = null;
        if (project != null && project.getTeamId() != null) {
            com.mokatest.platform.demos.domain.ui.Team team = teamMapper.selectById(project.getTeamId());
            teamName = team != null ? team.getTeamName() : null;
        }
        snapshot.put("projectName", projectName);
        snapshot.put("teamName", teamName);
    }

    /**
     * 根据项目ID获取团队ID
     */
    private Integer getTeamIdByProjectId(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        return project != null ? project.getTeamId() : null;
    }

    @Override
    public SaResult deleteComment(Integer id) {
        boolean success = removeById(id);
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }
}
