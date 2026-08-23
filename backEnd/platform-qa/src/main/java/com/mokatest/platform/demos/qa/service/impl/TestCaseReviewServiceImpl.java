package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.TestCaseReview;
import com.mokatest.platform.demos.qa.domain.TestCaseReviewCase;
import com.mokatest.platform.demos.qa.domain.TestCaseReviewItem;
import com.mokatest.platform.demos.qa.domain.TestCaseReviewReviewer;
import com.mokatest.platform.demos.qa.dto.TestCaseReviewBatchSubmitDTO;
import com.mokatest.platform.demos.qa.dto.TestCaseReviewSaveDTO;
import com.mokatest.platform.demos.qa.dto.TestCaseReviewSubmitDTO;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.mapper.QaModuleMapper;
import com.mokatest.platform.demos.qa.domain.QaModule;
import com.mokatest.platform.demos.qa.mapper.TestCaseReviewCaseMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseReviewItemMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseReviewMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseReviewReviewerMapper;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.message.service.impl.NotifyDispatchService;
import com.mokatest.platform.demos.qa.service.TestCaseOperationLogService;
import com.mokatest.platform.demos.qa.service.TestCaseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用例评审 Service 实现
 */
@Service
@RequiredArgsConstructor
public class TestCaseReviewServiceImpl extends ServiceImpl<TestCaseReviewMapper, TestCaseReview> implements TestCaseReviewService {

    private final TestCaseReviewCaseMapper reviewCaseMapper;
    private final TestCaseReviewItemMapper reviewItemMapper;
    private final TestCaseReviewReviewerMapper reviewReviewerMapper;
    private final com.mokatest.platform.demos.qa.mapper.TestCaseOperationLogMapper testCaseOperationLogMapper;
    private final TestCaseMapper testCaseMapper;
    private final QaModuleMapper qaModuleMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final TestCaseOperationLogService testCaseOperationLogService;
    private final NotifyDispatchService notifyDispatchService;
    private final MessageService messageService;

    @Override
    public SaResult listByProject(Integer projectId, Integer moduleId, String status, String keyword, Integer page, Integer pageSize) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<TestCaseReview> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (moduleId != null) {
            wrapper.eq("module_id", moduleId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("title", keyword).or().like("review_code", keyword));
        }
        wrapper.orderByDesc("create_time");
        IPage<TestCaseReview> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        IPage<TestCaseReview> result = baseMapper.selectPage(pageParam, wrapper);

        List<TestCaseReview> records = result.getRecords();
        List<Map<String, Object>> list = new ArrayList<>();
        if (!records.isEmpty()) {
            Map<Integer, String> userNameMap = loadUserNames(
                    records.stream().map(TestCaseReview::getCreatorId).filter(Objects::nonNull).collect(Collectors.toSet()));
            // 查询评审模块名称
            Set<Integer> moduleIds = records.stream()
                    .map(TestCaseReview::getModuleId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Integer, String> moduleNameMap = loadModuleNames(moduleIds);
            // 批量查询各评审单的评审人（供前端判断「评审」操作可用性）
            List<Integer> reviewIds = records.stream().map(TestCaseReview::getId).collect(Collectors.toList());
            QueryWrapper<TestCaseReviewReviewer> reviewerWrapper = new QueryWrapper<>();
            reviewerWrapper.in("review_id", reviewIds);
            Map<Integer, List<Integer>> reviewerIdsMap = reviewReviewerMapper.selectList(reviewerWrapper).stream()
                    .collect(Collectors.groupingBy(TestCaseReviewReviewer::getReviewId,
                            Collectors.mapping(TestCaseReviewReviewer::getReviewerId, Collectors.toList())));
            // 批量查询进度（避免 N+1：一次查出所有 cases 和 items）
            Map<Integer, Map<String, Object>> progressMap = batchBuildProgress(reviewIds);
            for (TestCaseReview review : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", review.getId());
                item.put("reviewCode", review.getReviewCode());
                item.put("projectId", review.getProjectId());
                item.put("moduleId", review.getModuleId());
                item.put("title", review.getTitle());
                item.put("description", review.getDescription());
                item.put("status", review.getStatus());
                item.put("passRule", review.getPassRule());
                item.put("deadline", review.getDeadline());
                item.put("resultSummary", review.getResultSummary());
                item.put("creatorId", review.getCreatorId());
                item.put("createTime", review.getCreateTime());
                item.put("updateTime", review.getUpdateTime());
                item.put("creatorName", userNameMap.getOrDefault(review.getCreatorId(), ""));
                item.put("moduleName", review.getModuleId() != null ? moduleNameMap.getOrDefault(review.getModuleId(), "") : "");
                item.put("reviewerIds", reviewerIdsMap.getOrDefault(review.getId(), Collections.emptyList()));
                item.putAll(progressMap.getOrDefault(review.getId(), Collections.emptyMap()));
                list.add(item);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", list);
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return SaResult.ok().setData(data);
    }

    @Override
    public SaResult myTodo() {
        return myTodo(null);
    }

    @Override
    public SaResult myTodo(Integer projectId) {
        Integer loginId = StpUtil.getLoginIdAsInt();
        // 我作为评审人的评审单
        QueryWrapper<TestCaseReviewReviewer> reviewerWrapper = new QueryWrapper<>();
        reviewerWrapper.eq("reviewer_id", loginId);
        List<TestCaseReviewReviewer> myReviewerRows = reviewReviewerMapper.selectList(reviewerWrapper);
        if (myReviewerRows.isEmpty()) {
            return SaResult.ok().setData(Collections.emptyList());
        }
        List<Integer> reviewIds = myReviewerRows.stream().map(TestCaseReviewReviewer::getReviewId).collect(Collectors.toList());
        QueryWrapper<TestCaseReview> reviewWrapper = new QueryWrapper<>();
        reviewWrapper.in("id", reviewIds).eq("status", "REVIEWING");
        if (projectId != null) {
            reviewWrapper.eq("project_id", projectId);
        }
        reviewWrapper.orderByDesc("create_time");
        List<TestCaseReview> reviews = baseMapper.selectList(reviewWrapper);
        if (reviews.isEmpty()) {
            return SaResult.ok().setData(Collections.emptyList());
        }
        // 只保留我还有 PENDING item 的评审单
        QueryWrapper<TestCaseReviewItem> pendingWrapper = new QueryWrapper<>();
        pendingWrapper.eq("reviewer_id", loginId).eq("conclusion", "PENDING")
                .in("review_id", reviews.stream().map(TestCaseReview::getId).collect(Collectors.toList()));
        List<TestCaseReviewItem> pendingItems = reviewItemMapper.selectList(pendingWrapper);
        Map<Integer, Long> pendingCountMap = pendingItems.stream()
                .collect(Collectors.groupingBy(TestCaseReviewItem::getReviewId, Collectors.counting()));

        Map<Integer, String> userNameMap = loadUserNames(
                reviews.stream().map(TestCaseReview::getCreatorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        // 批量查询进度（避免 N+1）
        List<Integer> allReviewIds = reviews.stream().map(TestCaseReview::getId).collect(Collectors.toList());
        Map<Integer, Map<String, Object>> progressMap = batchBuildProgress(allReviewIds);
        List<Map<String, Object>> list = new ArrayList<>();
        for (TestCaseReview review : reviews) {
            Long pendingCount = pendingCountMap.get(review.getId());
            if (pendingCount == null || pendingCount == 0) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("review", review);
            item.put("creatorName", userNameMap.getOrDefault(review.getCreatorId(), ""));
            item.put("myPendingCount", pendingCount);
            item.putAll(progressMap.getOrDefault(review.getId(), Collections.emptyMap()));
            list.add(item);
        }
        return SaResult.ok().setData(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult saveReview(TestCaseReviewSaveDTO dto) {
        if (dto == null || dto.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            return SaResult.error("评审标题不能为空");
        }
        // 圈选用例可选：允许先建空评审单，后续再通过追加用例圈入
        if (dto.getReviewerIds() == null || dto.getReviewerIds().isEmpty()) {
            return SaResult.error("请至少选择一名评审人");
        }
        String passRule = dto.getPassRule() != null ? dto.getPassRule() : "ALL";
        if (!"ALL".equals(passRule) && !"MAJORITY".equals(passRule)) {
            return SaResult.error("无效的通过规则: " + passRule);
        }
        // 校验用例存在且属于该项目
        List<Integer> caseIds = dto.getCaseIds() == null
                ? new ArrayList<>()
                : new ArrayList<>(new LinkedHashSet<>(dto.getCaseIds()));
        List<TestCase> cases = caseIds.isEmpty() ? new ArrayList<>() : testCaseMapper.selectBatchIds(caseIds);
        if (cases.size() != caseIds.size()) {
            return SaResult.error("存在无效的用例ID");
        }
        for (TestCase tc : cases) {
            if (!dto.getProjectId().equals(tc.getProjectId())) {
                return SaResult.error("用例 " + tc.getCaseCode() + " 不属于当前项目");
            }
        }
        List<Integer> reviewerIds = new ArrayList<>(new LinkedHashSet<>(dto.getReviewerIds()));

        Integer loginId = StpUtil.getLoginIdAsInt();
        Date now = new Date();

        // 1. 创建评审单（先 TMP- 占位，拿到自增 id 后改 REV-{id}）
        TestCaseReview review = new TestCaseReview();
        review.setReviewCode("TMP-" + System.nanoTime());
        review.setProjectId(dto.getProjectId());
        review.setModuleId(dto.getModuleId());
        review.setTitle(dto.getTitle());
        review.setDescription(dto.getDescription());
        review.setStatus("REVIEWING");
        review.setPassRule(passRule);
        review.setDeadline(dto.getDeadline());
        review.setCreatorId(loginId);
        review.setCreateTime(now);
        review.setUpdateTime(now);
        save(review);
        review.setReviewCode("REV-" + review.getId());
        updateById(review);

        // 2. 圈选用例 + 评审人 + 明细（case×reviewer 全组合 PENDING）
        for (Integer caseId : caseIds) {
            TestCaseReviewCase reviewCase = new TestCaseReviewCase();
            reviewCase.setReviewId(review.getId());
            reviewCase.setTestCaseId(caseId);
            reviewCase.setFinalResult("PENDING");
            reviewCase.setCreateTime(now);
            reviewCaseMapper.insert(reviewCase);
        }
        for (Integer reviewerId : reviewerIds) {
            TestCaseReviewReviewer reviewer = new TestCaseReviewReviewer();
            reviewer.setReviewId(review.getId());
            reviewer.setReviewerId(reviewerId);
            reviewReviewerMapper.insert(reviewer);
        }
        for (Integer caseId : caseIds) {
            for (Integer reviewerId : reviewerIds) {
                TestCaseReviewItem item = new TestCaseReviewItem();
                item.setReviewId(review.getId());
                item.setTestCaseId(caseId);
                item.setReviewerId(reviewerId);
                item.setConclusion("PENDING");
                reviewItemMapper.insert(item);
            }
        }

        // 3. 圈选的用例状态批量置为 REVIEWING，并写操作日志
        for (TestCase tc : cases) {
            if (!"REVIEWING".equals(tc.getStatus())) {
                String oldStatus = tc.getStatus();
                tc.setStatus("REVIEWING");
                tc.setUpdateUserId(loginId);
                tc.setUpdateTime(now);
                testCaseMapper.updateById(tc);
                testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status", oldStatus, "REVIEWING",
                        "加入评审单 " + review.getReviewCode());
            }
        }

        // 4. 站内信通知评审人
        sendReviewInvitedMessages(review, caseIds.size(), reviewerIds, loginId);
        return SaResult.ok("评审单创建成功").setData(review.getId());
    }

    @Override
    public SaResult getDetail(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        TestCaseReview review = getById(id);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        // 用例清单
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", id);
        List<TestCaseReviewCase> reviewCases = reviewCaseMapper.selectList(caseWrapper);
        // 评审明细
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", id);
        List<TestCaseReviewItem> items = reviewItemMapper.selectList(itemWrapper);
        // 评审人
        QueryWrapper<TestCaseReviewReviewer> reviewerWrapper = new QueryWrapper<>();
        reviewerWrapper.eq("review_id", id);
        List<TestCaseReviewReviewer> reviewers = reviewReviewerMapper.selectList(reviewerWrapper);

        // 用例信息（先查出用例，再合并所有用户ID一次性查名称）
        List<Integer> caseIds = reviewCases.stream().map(TestCaseReviewCase::getTestCaseId).collect(Collectors.toList());
        Map<Integer, TestCase> caseMap = new HashMap<>();
        if (!caseIds.isEmpty()) {
            List<TestCase> cases = testCaseMapper.selectBatchIds(caseIds);
            caseMap = cases.stream().collect(Collectors.toMap(TestCase::getId, c -> c, (a, b) -> a));
        }

        // 合并所有用户ID（评审创建人 + 评审人 + 用例创建人），一次性查询
        Set<Integer> userIds = new HashSet<>();
        if (review.getCreatorId() != null) {
            userIds.add(review.getCreatorId());
        }
        reviewers.forEach(r -> userIds.add(r.getReviewerId()));
        caseMap.values().stream().map(TestCase::getCreateUserId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Integer, String> userNameMap = loadUserNames(userIds);

        // 模块名称（用例所属模块）
        Set<Integer> caseModuleIds = caseMap.values().stream()
                .map(TestCase::getModuleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> moduleNameMap = loadModuleNames(caseModuleIds);

        // 明细按用例分组
        Map<Integer, List<TestCaseReviewItem>> itemMap = items.stream()
                .collect(Collectors.groupingBy(TestCaseReviewItem::getTestCaseId));

        List<Map<String, Object>> caseList = new ArrayList<>();
        for (TestCaseReviewCase rc : reviewCases) {
            Map<String, Object> row = new HashMap<>();
            TestCase tc = caseMap.get(rc.getTestCaseId());
            row.put("reviewCaseId", rc.getId());
            row.put("testCaseId", rc.getTestCaseId());
            row.put("finalResult", rc.getFinalResult());
            row.put("caseCode", tc != null ? tc.getCaseCode() : "");
            row.put("caseName", tc != null ? tc.getCaseName() : "");
            row.put("priority", tc != null ? tc.getPriority() : "");
            row.put("caseStatus", tc != null ? tc.getStatus() : "");
            row.put("moduleId", tc != null ? tc.getModuleId() : null);
            row.put("moduleName", tc != null && tc.getModuleId() != null ? moduleNameMap.getOrDefault(tc.getModuleId(), "") : "");
            row.put("caseCreatorId", tc != null ? tc.getCreateUserId() : null);
            row.put("caseCreatorName", tc != null && tc.getCreateUserId() != null ? userNameMap.getOrDefault(tc.getCreateUserId(), "") : "");
            List<Map<String, Object>> itemRows = new ArrayList<>();
            for (TestCaseReviewItem ri : itemMap.getOrDefault(rc.getTestCaseId(), Collections.emptyList())) {
                Map<String, Object> itemRow = new HashMap<>();
                itemRow.put("itemId", ri.getId());
                itemRow.put("reviewerId", ri.getReviewerId());
                itemRow.put("reviewerName", userNameMap.getOrDefault(ri.getReviewerId(), ""));
                itemRow.put("conclusion", ri.getConclusion());
                itemRow.put("comment", ri.getComment());
                itemRow.put("reviewTime", ri.getReviewTime());
                itemRows.add(itemRow);
            }
            row.put("items", itemRows);
            caseList.add(row);
        }

        List<Map<String, Object>> reviewerList = new ArrayList<>();
        for (TestCaseReviewReviewer r : reviewers) {
            Map<String, Object> row = new HashMap<>();
            row.put("reviewerId", r.getReviewerId());
            row.put("reviewerName", userNameMap.getOrDefault(r.getReviewerId(), ""));
            long done = items.stream().filter(i -> r.getReviewerId().equals(i.getReviewerId()) && !"PENDING".equals(i.getConclusion())).count();
            long total = items.stream().filter(i -> r.getReviewerId().equals(i.getReviewerId())).count();
            row.put("doneCount", done);
            row.put("totalCount", total);
            reviewerList.add(row);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", review.getId());
        data.put("reviewCode", review.getReviewCode());
        data.put("projectId", review.getProjectId());
        data.put("moduleId", review.getModuleId());
        data.put("title", review.getTitle());
        data.put("description", review.getDescription());
        data.put("status", review.getStatus());
        data.put("passRule", review.getPassRule());
        data.put("deadline", review.getDeadline());
        data.put("resultSummary", review.getResultSummary());
        data.put("creatorId", review.getCreatorId());
        data.put("creatorName", userNameMap.getOrDefault(review.getCreatorId(), ""));
        data.put("createTime", review.getCreateTime());
        data.put("updateTime", review.getUpdateTime());
        data.put("cases", caseList);
        data.put("reviewers", reviewerList);
        return SaResult.ok().setData(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult submit(TestCaseReviewSubmitDTO dto) {
        return submitOne(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult batchSubmit(TestCaseReviewBatchSubmitDTO dto) {
        if (dto == null || dto.getReviewId() == null) {
            return SaResult.error("缺少评审单ID");
        }
        if (dto.getTestCaseIds() == null || dto.getTestCaseIds().isEmpty()) {
            return SaResult.error("未选择用例");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        for (Integer testCaseId : new LinkedHashSet<>(dto.getTestCaseIds())) {
            TestCaseReviewSubmitDTO single = new TestCaseReviewSubmitDTO();
            single.setReviewId(dto.getReviewId());
            single.setTestCaseId(testCaseId);
            single.setConclusion(dto.getConclusion());
            single.setComment(dto.getComment());
            Map<String, Object> row = new HashMap<>();
            row.put("testCaseId", testCaseId);
            try {
                SaResult r = submitOne(single);
                boolean success = r.getCode() == 200;
                row.put("success", success);
                row.put("message", success ? "提交成功" : r.getMsg());
                if (success) {
                    successCount++;
                }
            } catch (Exception e) {
                row.put("success", false);
                row.put("message", "提交异常: " + e.getMessage());
            }
            results.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", results.size());
        data.put("successCount", successCount);
        data.put("results", results);
        return SaResult.ok("批量评审完成，成功 " + successCount + "/" + results.size() + " 条").setData(data);
    }

    /**
     * 单条提交评审结论核心逻辑（单条/批量共用）
     */
    private SaResult submitOne(TestCaseReviewSubmitDTO dto) {
        if (dto == null || dto.getReviewId() == null || dto.getTestCaseId() == null) {
            return SaResult.error("缺少参数");
        }
        if (dto.getConclusion() == null
                || (!"PASS".equals(dto.getConclusion()) && !"REJECT".equals(dto.getConclusion()))) {
            return SaResult.error("无效的结论: " + dto.getConclusion());
        }
        if ("REJECT".equals(dto.getConclusion()) && (dto.getComment() == null || dto.getComment().trim().isEmpty())) {
            return SaResult.error("打回时评审意见必填");
        }
        TestCaseReview review = getById(dto.getReviewId());
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        if ("CANCELLED".equals(review.getStatus())) {
            return SaResult.error("评审单已取消，无法提交");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", dto.getReviewId())
                .eq("test_case_id", dto.getTestCaseId())
                .eq("reviewer_id", loginId);
        TestCaseReviewItem item = reviewItemMapper.selectOne(itemWrapper);
        if (item == null) {
            return SaResult.error("您不是该用例的评审人");
        }
        // 已完结的评审单允许评审人继续评审：重开评审单 + 用例状态拨回 REVIEWING
        if ("COMPLETED".equals(review.getStatus())) {
            review.setStatus("REVIEWING");
            review.setResultSummary(null);
            review.setUpdateTime(new Date());
            updateById(review);
            TestCase tc = testCaseMapper.selectById(dto.getTestCaseId());
            if (tc != null && !"REVIEWING".equals(tc.getStatus())) {
                String oldStatus = tc.getStatus();
                tc.setStatus("REVIEWING");
                tc.setUpdateUserId(loginId);
                tc.setUpdateTime(new Date());
                testCaseMapper.updateById(tc);
                testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status", oldStatus, "REVIEWING",
                        "评审单 " + review.getReviewCode() + " 重新评审");
            }
        }
        // 已提交过允许修改重提：更新结论/意见/时间后重新汇总 finalResult
        // 1. 更新当前登录人的 item（结论或意见有变化时逐票留痕到操作日志）
        String oldConclusion = item.getConclusion();
        String oldComment = item.getComment();
        item.setConclusion(dto.getConclusion());
        item.setComment(dto.getComment());
        item.setReviewTime(new Date());
        reviewItemMapper.updateById(item);
        boolean voteChanged = !Objects.equals(oldConclusion, dto.getConclusion())
                || !Objects.equals(oldComment == null ? "" : oldComment, dto.getComment() == null ? "" : dto.getComment());
        if (voteChanged) {
            testCaseOperationLogService.logReviewVote(dto.getTestCaseId(), dto.getReviewId(),
                    oldConclusion, dto.getConclusion(), dto.getComment());
        }

        // 2. 该用例所有评审人已评完 → 按 pass_rule 定 final_result
        QueryWrapper<TestCaseReviewItem> caseItemWrapper = new QueryWrapper<>();
        caseItemWrapper.eq("review_id", dto.getReviewId()).eq("test_case_id", dto.getTestCaseId());
        List<TestCaseReviewItem> caseItems = reviewItemMapper.selectList(caseItemWrapper);
        boolean allDone = caseItems.stream().noneMatch(i -> "PENDING".equals(i.getConclusion()));
        if (allDone) {
            long passCount = caseItems.stream().filter(i -> "PASS".equals(i.getConclusion())).count();
            long rejectCount = caseItems.stream().filter(i -> "REJECT".equals(i.getConclusion())).count();
            String finalResult;
            if ("MAJORITY".equals(review.getPassRule())) {
                finalResult = passCount > rejectCount ? "PASS" : "REJECT";
            } else {
                finalResult = rejectCount == 0 ? "PASS" : "REJECT";
            }
            QueryWrapper<TestCaseReviewCase> rcWrapper = new QueryWrapper<>();
            rcWrapper.eq("review_id", dto.getReviewId()).eq("test_case_id", dto.getTestCaseId());
            TestCaseReviewCase reviewCase = reviewCaseMapper.selectOne(rcWrapper);
            if (reviewCase != null) {
                reviewCase.setFinalResult(finalResult);
                reviewCaseMapper.updateById(reviewCase);
            }
        }

        // 3. 整个评审单全部 case 都有 final_result → 自动 COMPLETED
        QueryWrapper<TestCaseReviewCase> allCaseWrapper = new QueryWrapper<>();
        allCaseWrapper.eq("review_id", dto.getReviewId());
        List<TestCaseReviewCase> allCases = reviewCaseMapper.selectList(allCaseWrapper);
        boolean reviewFinished = !allCases.isEmpty()
                && allCases.stream().noneMatch(rc -> "PENDING".equals(rc.getFinalResult()));
        if (reviewFinished) {
            completeReview(review, allCases, loginId);
        }
        return SaResult.ok("评审结论已提交");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult cancel(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        TestCaseReview review = getById(id);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        if (review.getCreatorId() == null || !review.getCreatorId().equals(loginId)) {
            return SaResult.error("仅创建人可取消评审单");
        }
        if (!"REVIEWING".equals(review.getStatus())) {
            return SaResult.error("评审单已结束，无法取消");
        }
        review.setStatus("CANCELLED");
        review.setUpdateTime(new Date());
        updateById(review);

        revertReviewingCases(review, loginId, "评审单 " + review.getReviewCode() + " 已取消");
        return SaResult.ok("评审单已取消");
    }

    /**
     * 圈中仍 REVIEWING 的用例回退 DRAFT（评审单取消/删除后结论作废，
     * 否则这些用例永远卡在 REVIEWING；若还在其他进行中评审单里则保持）
     */
    private void revertReviewingCases(TestCaseReview review, Integer loginId, String reason) {
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", review.getId());
        List<TestCaseReviewCase> reviewCases = reviewCaseMapper.selectList(caseWrapper);
        Date now = new Date();
        for (TestCaseReviewCase rc : reviewCases) {
            TestCase tc = testCaseMapper.selectById(rc.getTestCaseId());
            if (tc == null || !"REVIEWING".equals(tc.getStatus())) {
                continue;
            }
            QueryWrapper<TestCaseReviewCase> otherCaseWrapper = new QueryWrapper<>();
            otherCaseWrapper.eq("test_case_id", rc.getTestCaseId()).ne("review_id", review.getId());
            List<TestCaseReviewCase> otherCases = reviewCaseMapper.selectList(otherCaseWrapper);
            boolean hasOtherActive = false;
            for (TestCaseReviewCase oc : otherCases) {
                TestCaseReview otherReview = getById(oc.getReviewId());
                if (otherReview != null && "REVIEWING".equals(otherReview.getStatus())) {
                    hasOtherActive = true;
                    break;
                }
            }
            if (hasOtherActive) {
                continue;
            }
            tc.setStatus("DRAFT");
            tc.setUpdateUserId(loginId);
            tc.setUpdateTime(now);
            testCaseMapper.updateById(tc);
            testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status", "REVIEWING", "DRAFT", reason);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult deleteReview(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        TestCaseReview review = getById(id);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        if (review.getCreatorId() == null || !review.getCreatorId().equals(loginId)) {
            return SaResult.error("仅创建人可删除评审单");
        }
        // 评审中的单先按取消语义回退圈中用例状态
        if ("REVIEWING".equals(review.getStatus())) {
            revertReviewingCases(review, loginId, "评审单 " + review.getReviewCode() + " 已删除");
        }
        // 关联数据物理删除：评审明细 / 圈选用例 / 评审人
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", id);
        reviewItemMapper.delete(itemWrapper);
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", id);
        reviewCaseMapper.delete(caseWrapper);
        QueryWrapper<TestCaseReviewReviewer> reviewerWrapper = new QueryWrapper<>();
        reviewerWrapper.eq("review_id", id);
        reviewReviewerMapper.delete(reviewerWrapper);
        // 评审单本体逻辑删除（@TableLogic），列表/详情不再可见
        review.setDeletedAt(new Date());
        removeById(review);
        return SaResult.ok("评审单已删除");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult batchDeleteReview(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return SaResult.error("未选择要删除的评审单");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        // 一次性查出所有评审单
        List<TestCaseReview> reviews = baseMapper.selectBatchIds(ids);
        // 过滤：仅创建人可删
        List<TestCaseReview> deletable = reviews.stream()
                .filter(r -> r.getCreatorId() != null && r.getCreatorId().equals(loginId))
                .toList();
        if (deletable.isEmpty()) {
            return SaResult.error("没有可删除的评审单（仅创建人可删除）");
        }
        List<Integer> reviewIds = deletable.stream().map(TestCaseReview::getId).toList();
        // 评审中的单先回退圈中用例状态
        List<TestCaseReview> reviewing = deletable.stream()
                .filter(r -> "REVIEWING".equals(r.getStatus()))
                .toList();
        for (TestCaseReview review : reviewing) {
            revertReviewingCases(review, loginId, "评审单 " + review.getReviewCode() + " 已删除");
        }
        // 批量删除关联数据（IN 查询，一次搞定）
        reviewItemMapper.delete(new QueryWrapper<TestCaseReviewItem>().in("review_id", reviewIds));
        reviewCaseMapper.delete(new QueryWrapper<TestCaseReviewCase>().in("review_id", reviewIds));
        reviewReviewerMapper.delete(new QueryWrapper<TestCaseReviewReviewer>().in("review_id", reviewIds));
        // 批量逻辑删除
        Date now = new Date();
        for (TestCaseReview review : deletable) {
            review.setDeletedAt(now);
        }
        // MyBatis-Plus 批量更新
        updateBatchById(deletable);
        return SaResult.ok("批量删除完成，成功 " + deletable.size() + " 条");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult batchMoveModule(List<Integer> ids, Integer moduleId) {
        if (ids == null || ids.isEmpty()) {
            return SaResult.error("未选择要移动的评审单");
        }
        if (moduleId == null) {
            return SaResult.error("请选择目标模块");
        }
        Integer targetModuleId = moduleId == 0 ? null : moduleId;
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TestCaseReview> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.in("id", ids).set("module_id", targetModuleId);
        baseMapper.update(null, updateWrapper);
        return SaResult.ok("批量移动成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult resetCase(Integer reviewId, Integer testCaseId) {
        if (reviewId == null || testCaseId == null) {
            return SaResult.error("缺少参数");
        }
        TestCaseReview review = getById(reviewId);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        if (!review.getCreatorId().equals(loginId)) {
            return SaResult.error("仅创建人可重新评审");
        }
        if ("CANCELLED".equals(review.getStatus())) {
            return SaResult.error("评审单已取消，无法重新评审");
        }
        // 已完结的评审单 → 自动重新打开（与追加/移除用例逻辑一致）
        if ("COMPLETED".equals(review.getStatus())) {
            review.setStatus("REVIEWING");
            review.setResultSummary(null);
            review.setUpdateTime(new Date());
            updateById(review);
        }

        // 用例评审结果回退为待评审
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", reviewId).eq("test_case_id", testCaseId);
        TestCaseReviewCase rc = reviewCaseMapper.selectOne(caseWrapper);
        if (rc == null) {
            return SaResult.error("该用例未关联到此评审单");
        }
        rc.setFinalResult("PENDING");
        reviewCaseMapper.updateById(rc);

        // 评审明细全部重置为待评审（结论/意见/时间清空；null 字段必须走 UpdateWrapper.set）
        UpdateWrapper<TestCaseReviewItem> itemUpdate = new UpdateWrapper<>();
        itemUpdate.eq("review_id", reviewId).eq("test_case_id", testCaseId)
                .set("conclusion", "PENDING")
                .set("comment", null)
                .set("review_time", null);
        reviewItemMapper.update(null, itemUpdate);

        // 用例状态拨回 REVIEWING（与提交评审的重开逻辑一致）
        TestCase tc = testCaseMapper.selectById(testCaseId);
        if (tc != null && !"REVIEWING".equals(tc.getStatus())) {
            String oldStatus = tc.getStatus();
            tc.setStatus("REVIEWING");
            tc.setUpdateUserId(loginId);
            tc.setUpdateTime(new Date());
            testCaseMapper.updateById(tc);
            testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status", oldStatus, "REVIEWING",
                    "评审单 " + review.getReviewCode() + " 重新评审");
        }
        return SaResult.ok("已重置为待评审");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult removeCase(Integer reviewId, Integer testCaseId) {
        if (reviewId == null || testCaseId == null) {
            return SaResult.error("缺少参数");
        }
        TestCaseReview review = getById(reviewId);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        if (!review.getCreatorId().equals(loginId)) {
            return SaResult.error("仅创建人可移除关联用例");
        }
        // 已完结的评审单移除用例 → 自动重新打开（回到评审中），与追加用例逻辑一致
        if ("COMPLETED".equals(review.getStatus())) {
            review.setStatus("REVIEWING");
            review.setResultSummary(null);
            review.setUpdateTime(new Date());
            updateById(review);
        } else if (!"REVIEWING".equals(review.getStatus())) {
            return SaResult.error("评审单已取消，无法移除用例");
        }

        // 删除评审用例关联
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", reviewId).eq("test_case_id", testCaseId);
        TestCaseReviewCase rc = reviewCaseMapper.selectOne(caseWrapper);
        if (rc == null) {
            return SaResult.error("该用例未关联到此评审单");
        }
        reviewCaseMapper.delete(caseWrapper);

        // 删除该用例的评审明细
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", reviewId).eq("test_case_id", testCaseId);
        reviewItemMapper.delete(itemWrapper);

        // 移除后剩余用例可能已全部出结论（如移除了唯一待评的用例）→ 重算并检查是否自动完结
        recomputeFinalResults(reviewId, review.getPassRule());
        QueryWrapper<TestCaseReviewCase> remainWrapper = new QueryWrapper<>();
        remainWrapper.eq("review_id", reviewId);
        List<TestCaseReviewCase> remainCases = reviewCaseMapper.selectList(remainWrapper);
        boolean allFinished = !remainCases.isEmpty()
                && remainCases.stream().noneMatch(c -> "PENDING".equals(c.getFinalResult()));
        if (allFinished) {
            completeReview(review, remainCases, loginId);
        }

        // 若用例仍为 REVIEWING 且没有其他进行中的评审关联，回退为 DRAFT
        TestCase tc = testCaseMapper.selectById(testCaseId);
        Date now = new Date();
        if (tc != null && "REVIEWING".equals(tc.getStatus())) {
            // 检查是否还有其他进行中的评审包含此用例
            QueryWrapper<TestCaseReviewCase> otherCaseWrapper = new QueryWrapper<>();
            otherCaseWrapper.eq("test_case_id", testCaseId).ne("review_id", reviewId);
            List<TestCaseReviewCase> otherCases = reviewCaseMapper.selectList(otherCaseWrapper);
            boolean hasOtherActive = false;
            for (TestCaseReviewCase oc : otherCases) {
                TestCaseReview otherReview = getById(oc.getReviewId());
                if (otherReview != null && "REVIEWING".equals(otherReview.getStatus())) {
                    hasOtherActive = true;
                    break;
                }
            }
            if (!hasOtherActive) {
                tc.setStatus("DRAFT");
                tc.setUpdateUserId(loginId);
                tc.setUpdateTime(now);
                testCaseMapper.updateById(tc);
                testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status", "REVIEWING", "DRAFT",
                        "从评审单 " + review.getReviewCode() + " 移除");
            }
        }

        return SaResult.ok("已移除关联用例");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult addCases(Integer reviewId, List<Integer> caseIds) {
        if (reviewId == null || caseIds == null || caseIds.isEmpty()) {
            return SaResult.error("缺少参数");
        }
        TestCaseReview review = getById(reviewId);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        if (!review.getCreatorId().equals(loginId)) {
            return SaResult.error("仅创建人可追加用例");
        }
        // 已完结的评审单追加用例 → 自动重新打开（回到评审中）
        if ("COMPLETED".equals(review.getStatus())) {
            review.setStatus("REVIEWING");
            review.setResultSummary(null);
            review.setUpdateTime(new Date());
            updateById(review);
        } else if (!"REVIEWING".equals(review.getStatus())) {
            return SaResult.error("评审单已取消，无法追加用例");
        }

        // 已关联的用例 ID
        QueryWrapper<TestCaseReviewCase> existWrapper = new QueryWrapper<>();
        existWrapper.eq("review_id", reviewId);
        List<TestCaseReviewCase> existCases = reviewCaseMapper.selectList(existWrapper);
        Set<Integer> existIds = existCases.stream()
                .map(TestCaseReviewCase::getTestCaseId).collect(Collectors.toSet());

        // 过滤掉已关联的用例
        List<Integer> newCaseIds = new LinkedHashSet<>(caseIds).stream()
                .filter(id -> !existIds.contains(id))
                .collect(Collectors.toList());
        if (newCaseIds.isEmpty()) {
            return SaResult.error("所选用例均已关联到此评审单");
        }

        // 加载用例并校验项目
        List<TestCase> cases = testCaseMapper.selectBatchIds(newCaseIds);
        if (cases.size() != newCaseIds.size()) {
            return SaResult.error("存在无效的用例ID");
        }
        for (TestCase tc : cases) {
            if (!review.getProjectId().equals(tc.getProjectId())) {
                return SaResult.error("用例 " + tc.getCaseCode() + " 不属于当前项目");
            }
        }

        // 获取已有评审人
        QueryWrapper<TestCaseReviewReviewer> reviewerWrapper = new QueryWrapper<>();
        reviewerWrapper.eq("review_id", reviewId);
        List<TestCaseReviewReviewer> reviewers = reviewReviewerMapper.selectList(reviewerWrapper);
        List<Integer> reviewerIds = reviewers.stream()
                .map(TestCaseReviewReviewer::getReviewerId).collect(Collectors.toList());

        Date now = new Date();

        // 创建评审用例关联
        for (Integer caseId : newCaseIds) {
            TestCaseReviewCase reviewCase = new TestCaseReviewCase();
            reviewCase.setReviewId(reviewId);
            reviewCase.setTestCaseId(caseId);
            reviewCase.setFinalResult("PENDING");
            reviewCase.setCreateTime(now);
            reviewCaseMapper.insert(reviewCase);
        }

        // 创建评审明细（新用例 × 已有评审人，PENDING）
        for (Integer caseId : newCaseIds) {
            for (Integer reviewerId : reviewerIds) {
                TestCaseReviewItem item = new TestCaseReviewItem();
                item.setReviewId(reviewId);
                item.setTestCaseId(caseId);
                item.setReviewerId(reviewerId);
                item.setConclusion("PENDING");
                reviewItemMapper.insert(item);
            }
        }

        // 用例状态置为 REVIEWING
        for (TestCase tc : cases) {
            if (!"REVIEWING".equals(tc.getStatus())) {
                String oldStatus = tc.getStatus();
                tc.setStatus("REVIEWING");
                tc.setUpdateUserId(loginId);
                tc.setUpdateTime(now);
                testCaseMapper.updateById(tc);
                testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status", oldStatus, "REVIEWING",
                        "加入评审单 " + review.getReviewCode());
            }
        }

        return SaResult.ok("成功追加 " + newCaseIds.size() + " 条用例");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult updateCaseReviewers(Integer reviewId, Integer testCaseId, List<Integer> reviewerIds) {
        if (reviewId == null || testCaseId == null) {
            return SaResult.error("缺少参数");
        }
        TestCaseReview review = getById(reviewId);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        if (review.getCreatorId() == null || !review.getCreatorId().equals(loginId)) {
            return SaResult.error("仅创建人可调整评审人");
        }
        if (!"REVIEWING".equals(review.getStatus())) {
            return SaResult.error("评审单已结束，无法调整评审人");
        }
        // 用例须属于该评审单
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", reviewId).eq("test_case_id", testCaseId);
        if (reviewCaseMapper.selectOne(caseWrapper) == null) {
            return SaResult.error("该用例未关联到此评审单");
        }
        // 评审单级评审人 = 默认评审人
        QueryWrapper<TestCaseReviewReviewer> reviewerWrapper = new QueryWrapper<>();
        reviewerWrapper.eq("review_id", reviewId);
        Set<Integer> validReviewerIds = reviewReviewerMapper.selectList(reviewerWrapper).stream()
                .map(TestCaseReviewReviewer::getReviewerId).collect(Collectors.toSet());
        Set<Integer> newReviewerIds;
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            // 清空该用例评审人 → 自动回退为默认评审人
            newReviewerIds = new LinkedHashSet<>(validReviewerIds);
        } else {
            newReviewerIds = new LinkedHashSet<>(reviewerIds);
            for (Integer rid : newReviewerIds) {
                if (!validReviewerIds.contains(rid)) {
                    return SaResult.error("评审人 " + rid + " 不在本评审单的评审人列表中");
                }
            }
        }
        if (newReviewerIds.isEmpty()) {
            return SaResult.error("评审单缺少默认评审人，无法调整");
        }

        // diff 该用例的评审明细
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", reviewId).eq("test_case_id", testCaseId);
        List<TestCaseReviewItem> existingItems = reviewItemMapper.selectList(itemWrapper);
        Set<Integer> oldReviewerIds = existingItems.stream()
                .map(TestCaseReviewItem::getReviewerId).collect(Collectors.toSet());

        Set<Integer> toRemove = new LinkedHashSet<>(oldReviewerIds);
        toRemove.removeAll(newReviewerIds);
        Set<Integer> toAdd = new LinkedHashSet<>(newReviewerIds);
        toAdd.removeAll(oldReviewerIds);

        if (!toRemove.isEmpty()) {
            QueryWrapper<TestCaseReviewItem> removeWrapper = new QueryWrapper<>();
            removeWrapper.eq("review_id", reviewId).eq("test_case_id", testCaseId)
                    .in("reviewer_id", toRemove);
            reviewItemMapper.delete(removeWrapper);
        }
        for (Integer rid : toAdd) {
            TestCaseReviewItem item = new TestCaseReviewItem();
            item.setReviewId(reviewId);
            item.setTestCaseId(testCaseId);
            item.setReviewerId(rid);
            item.setConclusion("PENDING");
            reviewItemMapper.insert(item);
        }

        // 重算 finalResult（整单重算，开销小且保证一致）
        recomputeFinalResults(reviewId, review.getPassRule());

        // 重算后若所有用例均已出结论，直接完结评审单
        QueryWrapper<TestCaseReviewCase> allCaseWrapper = new QueryWrapper<>();
        allCaseWrapper.eq("review_id", reviewId);
        List<TestCaseReviewCase> allCases = reviewCaseMapper.selectList(allCaseWrapper);
        boolean allFinished = !allCases.isEmpty()
                && allCases.stream().noneMatch(rc -> "PENDING".equals(rc.getFinalResult()));
        if (allFinished) {
            completeReview(review, allCases, loginId);
        }
        return SaResult.ok("评审人已调整");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult updateReview(Integer id, TestCaseReviewSaveDTO dto) {
        if (id == null || dto == null) {
            return SaResult.error("缺少参数");
        }
        TestCaseReview review = getById(id);
        if (review == null) {
            return SaResult.error("评审单不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        if (!review.getCreatorId().equals(loginId)) {
            return SaResult.error("仅创建人可编辑评审单");
        }
        if (!"REVIEWING".equals(review.getStatus())) {
            return SaResult.error("评审单已结束，无法编辑");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            return SaResult.error("评审标题不能为空");
        }
        if (dto.getReviewerIds() == null || dto.getReviewerIds().isEmpty()) {
            return SaResult.error("请至少选择一名评审人");
        }
        // 通过规则仅新建时可定；编辑忽略传入值，以原评审单为准（改规则会让已投结论的口径失效）
        String passRule = review.getPassRule();

        Date now = new Date();

        // 1. 更新基本信息（passRule 保持原值，不更新）
        review.setTitle(dto.getTitle());
        review.setDescription(dto.getDescription());
        review.setDeadline(dto.getDeadline());
        review.setModuleId(dto.getModuleId());
        review.setUpdateTime(now);
        updateById(review);

        // 2. diff 评审人
        QueryWrapper<TestCaseReviewReviewer> reviewerWrapper = new QueryWrapper<>();
        reviewerWrapper.eq("review_id", id);
        List<TestCaseReviewReviewer> existingReviewers = reviewReviewerMapper.selectList(reviewerWrapper);
        Set<Integer> oldReviewerIds = existingReviewers.stream()
                .map(TestCaseReviewReviewer::getReviewerId).collect(Collectors.toSet());
        Set<Integer> newReviewerIds = new LinkedHashSet<>(dto.getReviewerIds());

        // 新增的评审人
        Set<Integer> toAddReviewers = new LinkedHashSet<>(newReviewerIds);
        toAddReviewers.removeAll(oldReviewerIds);
        // 移除的评审人
        Set<Integer> toRemoveReviewers = new LinkedHashSet<>(oldReviewerIds);
        toRemoveReviewers.removeAll(newReviewerIds);

        if (!toRemoveReviewers.isEmpty()) {
            // 删除评审人记录
            QueryWrapper<TestCaseReviewReviewer> removeReviewerWrapper = new QueryWrapper<>();
            removeReviewerWrapper.eq("review_id", id).in("reviewer_id", toRemoveReviewers);
            reviewReviewerMapper.delete(removeReviewerWrapper);
            // 删除这些评审人的评审明细
            QueryWrapper<TestCaseReviewItem> removeItemWrapper = new QueryWrapper<>();
            removeItemWrapper.eq("review_id", id).in("reviewer_id", toRemoveReviewers);
            reviewItemMapper.delete(removeItemWrapper);
        }

        if (!toAddReviewers.isEmpty()) {
            // 获取当前所有关联用例
            QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
            caseWrapper.eq("review_id", id);
            List<TestCaseReviewCase> existingCases = reviewCaseMapper.selectList(caseWrapper);
            List<Integer> caseIds = existingCases.stream()
                    .map(TestCaseReviewCase::getTestCaseId).collect(Collectors.toList());

            // 新增评审人记录 + 评审明细（所有用例 × 新评审人，PENDING）
            for (Integer reviewerId : toAddReviewers) {
                TestCaseReviewReviewer reviewer = new TestCaseReviewReviewer();
                reviewer.setReviewId(id);
                reviewer.setReviewerId(reviewerId);
                reviewReviewerMapper.insert(reviewer);
                for (Integer caseId : caseIds) {
                    TestCaseReviewItem item = new TestCaseReviewItem();
                    item.setReviewId(id);
                    item.setTestCaseId(caseId);
                    item.setReviewerId(reviewerId);
                    item.setConclusion("PENDING");
                    reviewItemMapper.insert(item);
                }
            }
            // 站内信通知新增评审人
            sendReviewInvitedMessages(review, caseIds.size(),
                    new ArrayList<>(toAddReviewers), loginId);
        }

        // 3. diff 用例
        QueryWrapper<TestCaseReviewCase> caseQueryWrapper = new QueryWrapper<>();
        caseQueryWrapper.eq("review_id", id);
        List<TestCaseReviewCase> existingCases = reviewCaseMapper.selectList(caseQueryWrapper);
        Set<Integer> oldCaseIds = existingCases.stream()
                .map(TestCaseReviewCase::getTestCaseId).collect(Collectors.toSet());
        Set<Integer> newCaseIds = dto.getCaseIds() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(dto.getCaseIds());

        // 新增的用例
        List<Integer> toAddCaseIds = new LinkedHashSet<>(newCaseIds).stream()
                .filter(cid -> !oldCaseIds.contains(cid))
                .collect(Collectors.toList());
        // 移除的用例
        List<Integer> toRemoveCaseIds = oldCaseIds.stream()
                .filter(cid -> !newCaseIds.contains(cid))
                .collect(Collectors.toList());

        if (!toRemoveCaseIds.isEmpty()) {
            for (Integer testCaseId : toRemoveCaseIds) {
                // 删除评审用例关联
                QueryWrapper<TestCaseReviewCase> rcWrapper = new QueryWrapper<>();
                rcWrapper.eq("review_id", id).eq("test_case_id", testCaseId);
                reviewCaseMapper.delete(rcWrapper);
                // 删除评审明细
                QueryWrapper<TestCaseReviewItem> riWrapper = new QueryWrapper<>();
                riWrapper.eq("review_id", id).eq("test_case_id", testCaseId);
                reviewItemMapper.delete(riWrapper);
                // 若用例仍为 REVIEWING 且没有其他进行中的评审关联，回退为 DRAFT
                TestCase tc = testCaseMapper.selectById(testCaseId);
                if (tc != null && "REVIEWING".equals(tc.getStatus())) {
                    QueryWrapper<TestCaseReviewCase> otherWrapper = new QueryWrapper<>();
                    otherWrapper.eq("test_case_id", testCaseId).ne("review_id", id);
                    List<TestCaseReviewCase> otherCases = reviewCaseMapper.selectList(otherWrapper);
                    boolean hasOtherActive = false;
                    for (TestCaseReviewCase oc : otherCases) {
                        TestCaseReview otherReview = getById(oc.getReviewId());
                        if (otherReview != null && "REVIEWING".equals(otherReview.getStatus())) {
                            hasOtherActive = true;
                            break;
                        }
                    }
                    if (!hasOtherActive) {
                        tc.setStatus("DRAFT");
                        tc.setUpdateUserId(loginId);
                        tc.setUpdateTime(now);
                        testCaseMapper.updateById(tc);
                        testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status",
                                "REVIEWING", "DRAFT",
                                "从评审单 " + review.getReviewCode() + " 移除");
                    }
                }
            }
        }

        if (!toAddCaseIds.isEmpty()) {
            // 校验新用例存在且属于该项目
            List<TestCase> cases = testCaseMapper.selectBatchIds(toAddCaseIds);
            if (cases.size() != toAddCaseIds.size()) {
                return SaResult.error("存在无效的用例ID");
            }
            for (TestCase tc : cases) {
                if (!review.getProjectId().equals(tc.getProjectId())) {
                    return SaResult.error("用例 " + tc.getCaseCode() + " 不属于当前项目");
                }
            }
            // 获取当前所有评审人（更新后的）
            QueryWrapper<TestCaseReviewReviewer> updatedReviewerWrapper = new QueryWrapper<>();
            updatedReviewerWrapper.eq("review_id", id);
            List<TestCaseReviewReviewer> updatedReviewers = reviewReviewerMapper.selectList(updatedReviewerWrapper);
            List<Integer> reviewerIds = updatedReviewers.stream()
                    .map(TestCaseReviewReviewer::getReviewerId).collect(Collectors.toList());

            for (Integer caseId : toAddCaseIds) {
                TestCaseReviewCase rc = new TestCaseReviewCase();
                rc.setReviewId(id);
                rc.setTestCaseId(caseId);
                rc.setFinalResult("PENDING");
                rc.setCreateTime(now);
                reviewCaseMapper.insert(rc);
                for (Integer reviewerId : reviewerIds) {
                    TestCaseReviewItem item = new TestCaseReviewItem();
                    item.setReviewId(id);
                    item.setTestCaseId(caseId);
                    item.setReviewerId(reviewerId);
                    item.setConclusion("PENDING");
                    reviewItemMapper.insert(item);
                }
            }
            // 用例状态置为 REVIEWING
            for (TestCase tc : cases) {
                if (!"REVIEWING".equals(tc.getStatus())) {
                    String oldStatus = tc.getStatus();
                    tc.setStatus("REVIEWING");
                    tc.setUpdateUserId(loginId);
                    tc.setUpdateTime(now);
                    testCaseMapper.updateById(tc);
                    testCaseOperationLogService.log(tc.getId(), "TRANSITION", "status",
                            oldStatus, "REVIEWING",
                            "加入评审单 " + review.getReviewCode());
                }
            }
        }

        // 4. 评审人/用例变动后，重算所有用例 finalResult（旧结论可能基于已删除的明细得出）
        recomputeFinalResults(id, passRule);

        // 5. 重算后若所有用例均已出结论（如移除了未评审的评审人），直接完结评审单
        QueryWrapper<TestCaseReviewCase> finalCaseWrapper = new QueryWrapper<>();
        finalCaseWrapper.eq("review_id", id);
        List<TestCaseReviewCase> finalCases = reviewCaseMapper.selectList(finalCaseWrapper);
        boolean allFinished = !finalCases.isEmpty()
                && finalCases.stream().noneMatch(rc -> "PENDING".equals(rc.getFinalResult()));
        if (allFinished) {
            completeReview(review, finalCases, loginId);
        }

        return SaResult.ok("评审单已更新");
    }

    @Override
    public SaResult getCaseReviewHistory(Integer testCaseId) {
        if (testCaseId == null) {
            return SaResult.error("缺少用例ID");
        }
        // 投票事件流（REVIEW_VOTE 操作日志，逐票留痕，含改票历史）
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseOperationLog> voteWrapper = new QueryWrapper<>();
        voteWrapper.eq("test_case_id", testCaseId).eq("operation", "REVIEW_VOTE")
                .orderByDesc("operate_time").orderByDesc("id");
        List<com.mokatest.platform.demos.qa.domain.TestCaseOperationLog> votes = testCaseOperationLogMapper.selectList(voteWrapper);

        if (!votes.isEmpty()) {
            Set<Integer> reviewIds = votes.stream().map(com.mokatest.platform.demos.qa.domain.TestCaseOperationLog::getReviewId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Integer, TestCaseReview> reviewMap = reviewIds.isEmpty() ? Collections.emptyMap()
                    : listByIds(reviewIds).stream().collect(Collectors.toMap(TestCaseReview::getId, r -> r, (a, b) -> a));
            // 评审人昵称 + 头像
            Set<Integer> reviewerIds = votes.stream().map(com.mokatest.platform.demos.qa.domain.TestCaseOperationLog::getOperatorId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Integer, User> userMap = reviewerIds.isEmpty() ? Collections.emptyMap()
                    : userMapper.selectBatchIds(reviewerIds).stream().collect(Collectors.toMap(u -> u.getId().intValue(), u -> u, (a, b) -> a));

            List<Map<String, Object>> historyList = new ArrayList<>();
            for (com.mokatest.platform.demos.qa.domain.TestCaseOperationLog vote : votes) {
                Map<String, Object> row = new HashMap<>();
                TestCaseReview review = vote.getReviewId() != null ? reviewMap.get(vote.getReviewId()) : null;
                row.put("reviewId", vote.getReviewId());
                row.put("reviewCode", review != null ? review.getReviewCode() : "");
                row.put("reviewTitle", review != null ? review.getTitle() : "");
                row.put("reviewStatus", review != null ? review.getStatus() : "");
                row.put("reviewerId", vote.getOperatorId());
                User u = vote.getOperatorId() != null ? userMap.get(vote.getOperatorId()) : null;
                row.put("reviewerName", u != null
                        ? (u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername())
                        : (vote.getOperatorName() != null ? vote.getOperatorName() : ""));
                row.put("reviewerAvatar", u != null ? u.getAvatar() : null);
                row.put("oldConclusion", vote.getOldValue());
                row.put("conclusion", vote.getNewValue());
                row.put("comment", vote.getComment());
                row.put("reviewTime", vote.getOperateTime());
                historyList.add(row);
            }
            return SaResult.ok().setData(historyList);
        }

        // 存量数据兜底：还没有 REVIEW_VOTE 日志的老评审，按明细当前状态展示
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("test_case_id", testCaseId).orderByDesc("review_time");
        List<TestCaseReviewItem> items = reviewItemMapper.selectList(itemWrapper);

        if (items.isEmpty()) {
            return SaResult.ok().setData(Collections.emptyList());
        }

        // 获取所有关联的评审单
        Set<Integer> reviewIds = items.stream().map(TestCaseReviewItem::getReviewId).collect(Collectors.toSet());
        List<TestCaseReview> reviews = listByIds(reviewIds);
        Map<Integer, TestCaseReview> reviewMap = reviews.stream()
                .collect(Collectors.toMap(TestCaseReview::getId, r -> r, (a, b) -> a));

        // 获取评审人名称
        Set<Integer> reviewerIds = items.stream().map(TestCaseReviewItem::getReviewerId).collect(Collectors.toSet());
        Map<Integer, String> reviewerNameMap = loadUserNames(reviewerIds);

        // 获取用例在各评审中的最终结论
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("test_case_id", testCaseId);
        List<TestCaseReviewCase> reviewCases = reviewCaseMapper.selectList(caseWrapper);
        Map<Integer, String> finalResultMap = reviewCases.stream()
                .collect(Collectors.toMap(TestCaseReviewCase::getReviewId, TestCaseReviewCase::getFinalResult, (a, b) -> a));

        List<Map<String, Object>> historyList = new ArrayList<>();
        for (TestCaseReviewItem item : items) {
            TestCaseReview review = reviewMap.get(item.getReviewId());
            if (review == null) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("reviewId", review.getId());
            row.put("reviewCode", review.getReviewCode());
            row.put("reviewTitle", review.getTitle());
            row.put("reviewStatus", review.getStatus());
            row.put("passRule", review.getPassRule());
            row.put("finalResult", finalResultMap.getOrDefault(review.getId(), "PENDING"));
            row.put("reviewerId", item.getReviewerId());
            row.put("reviewerName", reviewerNameMap.getOrDefault(item.getReviewerId(), ""));
            row.put("conclusion", item.getConclusion());
            row.put("comment", item.getComment());
            row.put("reviewTime", item.getReviewTime());
            historyList.add(row);
        }
        return SaResult.ok().setData(historyList);
    }

    /**
     * 完成评审单：更新用例状态、写操作日志、汇总结论、发送通知
     */
    private void completeReview(TestCaseReview review, List<TestCaseReviewCase> allCases, Integer operatorId) {
        Date now = new Date();
        long passCount = allCases.stream().filter(rc -> "PASS".equals(rc.getFinalResult())).count();
        long rejectCount = allCases.stream().filter(rc -> "REJECT".equals(rc.getFinalResult())).count();

        // 汇总每条用例的评审意见
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", review.getId());
        List<TestCaseReviewItem> allItems = reviewItemMapper.selectList(itemWrapper);
        Map<Integer, List<TestCaseReviewItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(TestCaseReviewItem::getTestCaseId));

        List<Integer> rejectedCaseIds = new ArrayList<>();
        for (TestCaseReviewCase rc : allCases) {
            TestCase tc = testCaseMapper.selectById(rc.getTestCaseId());
            if (tc == null) {
                continue;
            }
            String comment = aggregateComments(itemMap.getOrDefault(rc.getTestCaseId(), Collections.emptyList()));
            String newStatus = "PASS".equals(rc.getFinalResult()) ? "REVIEWED" : "DRAFT";
            String oldStatus = tc.getStatus();
            tc.setStatus(newStatus);
            tc.setUpdateUserId(operatorId);
            tc.setUpdateTime(now);
            testCaseMapper.updateById(tc);
            testCaseOperationLogService.log(tc.getId(),
                    "PASS".equals(rc.getFinalResult()) ? "REVIEW_PASS" : "REVIEW_REJECT",
                    "status", oldStatus, newStatus, comment);
            if ("REJECT".equals(rc.getFinalResult())) {
                rejectedCaseIds.add(rc.getTestCaseId());
            }
        }

        String summary = "通过" + passCount + "条/打回" + rejectCount + "条";
        review.setStatus("COMPLETED");
        review.setResultSummary(summary);
        review.setUpdateTime(now);
        updateById(review);

        // 站内信通知创建人 + 被REJECT用例的创建人
        sendReviewCompletedMessages(review, summary, rejectedCaseIds, operatorId);
    }

    private String aggregateComments(List<TestCaseReviewItem> items) {
        Set<Integer> userIds = items.stream().map(TestCaseReviewItem::getReviewerId).collect(Collectors.toSet());
        Map<Integer, String> nameMap = loadUserNames(userIds);
        List<String> parts = new ArrayList<>();
        for (TestCaseReviewItem item : items) {
            if (item.getComment() != null && !item.getComment().isEmpty()) {
                String name = nameMap.getOrDefault(item.getReviewerId(), String.valueOf(item.getReviewerId()));
                parts.add(name + ": " + item.getComment());
            }
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    /**
     * 评审邀请通知（通知所有评审人）
     */
    private void sendReviewInvitedMessages(TestCaseReview review, int caseCount, List<Integer> reviewerIds, Integer loginId) {
        Map<String, Object> params = new HashMap<>();
        params.put("reviewCode", review.getReviewCode());
        params.put("reviewTitle", review.getTitle());
        params.put("caseCount", String.valueOf(caseCount));
        params.put("operatorName", getOperatorName(loginId));
        params.put("projectName", getProjectName(review.getProjectId()));
        List<MessageContext> contexts = new ArrayList<>();
        for (Integer reviewerId : reviewerIds) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REVIEW_INVITED)
                    .senderId(loginId)
                    .receiverId(reviewerId)
                    .receiverRole("REVIEWER")
                    .projectId(review.getProjectId())
                    .teamId(getTeamIdByProjectId(review.getProjectId()))
                    .bizType("testCase")
                    .bizId(review.getId())
                    .params(params)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 评审完成通知（通知创建人 + 被REJECT用例的创建人）
     */
    private void sendReviewCompletedMessages(TestCaseReview review, String summary, List<Integer> rejectedCaseIds, Integer operatorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("reviewCode", review.getReviewCode());
        params.put("reviewTitle", review.getTitle());
        params.put("resultSummary", summary);
        params.put("operatorName", getOperatorName(operatorId));
        params.put("projectName", getProjectName(review.getProjectId()));
        List<MessageContext> contexts = new ArrayList<>();
        Set<Integer> notified = new HashSet<>();
        if (review.getCreatorId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REVIEW_COMPLETED)
                    .senderId(operatorId)
                    .receiverId(review.getCreatorId())
                    .receiverRole("CREATOR")
                    .projectId(review.getProjectId())
                    .teamId(getTeamIdByProjectId(review.getProjectId()))
                    .bizType("testCase")
                    .bizId(review.getId())
                    .params(params)
                    .build());
            notified.add(review.getCreatorId());
        }
        if (!rejectedCaseIds.isEmpty()) {
            List<TestCase> rejectedCases = testCaseMapper.selectBatchIds(rejectedCaseIds);
            Set<Integer> caseCreatorIds = rejectedCases.stream()
                    .map(TestCase::getCreateUserId)
                    .filter(Objects::nonNull)
                    .filter(cid -> !notified.contains(cid))
                    .collect(Collectors.toSet());
            for (Integer creatorId : caseCreatorIds) {
                if (notifyDispatchService.isRoleEnabled(review.getProjectId(), MessageEventType.REVIEW_COMPLETED, "CASE_CREATOR")) {
                    contexts.add(MessageContext.builder()
                            .eventType(MessageEventType.REVIEW_COMPLETED)
                            .senderId(operatorId)
                            .receiverId(creatorId)
                            .receiverRole("CASE_CREATOR")
                            .projectId(review.getProjectId())
                            .teamId(getTeamIdByProjectId(review.getProjectId()))
                            .bizType("testCase")
                            .bizId(review.getId())
                            .params(params)
                            .build());
                }
            }
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFromActiveReviews(List<Integer> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            return;
        }
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.in("test_case_id", testCaseIds);
        List<TestCaseReviewCase> rows = reviewCaseMapper.selectList(caseWrapper);
        if (rows.isEmpty()) {
            return;
        }
        // 按评审单分组，仅处理「评审中」的（已完成/已取消的保留历史记录）
        Map<Integer, List<TestCaseReviewCase>> byReview = rows.stream()
                .collect(Collectors.groupingBy(TestCaseReviewCase::getReviewId));
        Integer loginId = StpUtil.getLoginIdAsInt();
        for (Map.Entry<Integer, List<TestCaseReviewCase>> entry : byReview.entrySet()) {
            TestCaseReview review = getById(entry.getKey());
            if (review == null || !"REVIEWING".equals(review.getStatus())) {
                continue;
            }
            List<Integer> caseIds = entry.getValue().stream()
                    .map(TestCaseReviewCase::getTestCaseId).collect(Collectors.toList());
            QueryWrapper<TestCaseReviewCase> delCase = new QueryWrapper<>();
            delCase.eq("review_id", entry.getKey()).in("test_case_id", caseIds);
            reviewCaseMapper.delete(delCase);
            QueryWrapper<TestCaseReviewItem> delItem = new QueryWrapper<>();
            delItem.eq("review_id", entry.getKey()).in("test_case_id", caseIds);
            reviewItemMapper.delete(delItem);

            recomputeFinalResults(entry.getKey(), review.getPassRule());

            // 剩余用例全部出结论（或已圈用例被删光）→ 完结评审单
            QueryWrapper<TestCaseReviewCase> remainWrapper = new QueryWrapper<>();
            remainWrapper.eq("review_id", entry.getKey());
            List<TestCaseReviewCase> remainCases = reviewCaseMapper.selectList(remainWrapper);
            boolean finished = remainCases.stream().noneMatch(rc -> "PENDING".equals(rc.getFinalResult()));
            if (finished) {
                completeReview(review, remainCases, loginId);
            }
        }
    }

    /**
     * 重算评审单内所有用例的 finalResult：
     * 明细全部评完 → 按 passRule 汇总；仍有 PENDING → 回退 PENDING（如新增评审人后）
     */
    private void recomputeFinalResults(Integer reviewId, String passRule) {
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", reviewId);
        List<TestCaseReviewCase> reviewCases = reviewCaseMapper.selectList(caseWrapper);
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", reviewId);
        List<TestCaseReviewItem> items = reviewItemMapper.selectList(itemWrapper);
        Map<Integer, List<TestCaseReviewItem>> itemMap = items.stream()
                .collect(Collectors.groupingBy(TestCaseReviewItem::getTestCaseId));
        for (TestCaseReviewCase rc : reviewCases) {
            List<TestCaseReviewItem> caseItems = itemMap.getOrDefault(rc.getTestCaseId(), Collections.emptyList());
            String newResult;
            if (caseItems.isEmpty() || caseItems.stream().anyMatch(i -> "PENDING".equals(i.getConclusion()))) {
                newResult = "PENDING";
            } else {
                long passCount = caseItems.stream().filter(i -> "PASS".equals(i.getConclusion())).count();
                long rejectCount = caseItems.stream().filter(i -> "REJECT".equals(i.getConclusion())).count();
                if ("MAJORITY".equals(passRule)) {
                    newResult = passCount > rejectCount ? "PASS" : "REJECT";
                } else {
                    newResult = rejectCount == 0 ? "PASS" : "REJECT";
                }
            }
            if (!newResult.equals(rc.getFinalResult())) {
                rc.setFinalResult(newResult);
                reviewCaseMapper.updateById(rc);
            }
        }
    }

    /**
     * 批量查询多个评审单的进度（避免 N+1：2 条 SQL 搞定全部）
     */
    private Map<Integer, Map<String, Object>> batchBuildProgress(List<Integer> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 一次查出所有评审用例
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.in("review_id", reviewIds);
        List<TestCaseReviewCase> allCases = reviewCaseMapper.selectList(caseWrapper);
        Map<Integer, List<TestCaseReviewCase>> casesByReview = allCases.stream()
                .collect(Collectors.groupingBy(TestCaseReviewCase::getReviewId));

        // 一次查出所有评审明细
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.in("review_id", reviewIds);
        List<TestCaseReviewItem> allItems = reviewItemMapper.selectList(itemWrapper);
        Map<Integer, List<TestCaseReviewItem>> itemsByReview = allItems.stream()
                .collect(Collectors.groupingBy(TestCaseReviewItem::getReviewId));

        Map<Integer, Map<String, Object>> result = new HashMap<>();
        for (Integer rid : reviewIds) {
            List<TestCaseReviewCase> rcs = casesByReview.getOrDefault(rid, Collections.emptyList());
            List<TestCaseReviewItem> items = itemsByReview.getOrDefault(rid, Collections.emptyList());
            Map<String, Object> progress = new HashMap<>();
            progress.put("caseCount", rcs.size());
            progress.put("finishedCaseCount", rcs.stream().filter(rc -> !"PENDING".equals(rc.getFinalResult())).count());
            progress.put("passCount", rcs.stream().filter(rc -> "PASS".equals(rc.getFinalResult())).count());
            progress.put("rejectCount", rcs.stream().filter(rc -> "REJECT".equals(rc.getFinalResult())).count());
            progress.put("itemCount", items.size());
            progress.put("finishedItemCount", items.stream().filter(i -> !"PENDING".equals(i.getConclusion())).count());
            result.put(rid, progress);
        }
        return result;
    }

    /**
     * 评审单进度统计（单条，供 getDetail 等单条场景使用）
     */
    private Map<String, Object> buildProgress(Integer reviewId) {
        QueryWrapper<TestCaseReviewCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("review_id", reviewId);
        List<TestCaseReviewCase> reviewCases = reviewCaseMapper.selectList(caseWrapper);
        QueryWrapper<TestCaseReviewItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("review_id", reviewId);
        List<TestCaseReviewItem> items = reviewItemMapper.selectList(itemWrapper);
        Map<String, Object> progress = new HashMap<>();
        progress.put("caseCount", reviewCases.size());
        progress.put("finishedCaseCount", reviewCases.stream().filter(rc -> !"PENDING".equals(rc.getFinalResult())).count());
        progress.put("passCount", reviewCases.stream().filter(rc -> "PASS".equals(rc.getFinalResult())).count());
        progress.put("rejectCount", reviewCases.stream().filter(rc -> "REJECT".equals(rc.getFinalResult())).count());
        progress.put("itemCount", items.size());
        progress.put("finishedItemCount", items.stream().filter(i -> !"PENDING".equals(i.getConclusion())).count());
        return progress;
    }

    private Map<Integer, String> loadUserNames(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(u -> u.getId().intValue(),
                u -> u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername(),
                (a, b) -> a));
    }

    private Map<Integer, String> loadModuleNames(Set<Integer> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<QaModule> modules = qaModuleMapper.selectBatchIds(moduleIds);
        return modules.stream().collect(Collectors.toMap(QaModule::getId, QaModule::getModuleName, (a, b) -> a));
    }

    private String getOperatorName(Integer userId) {
        if (userId == null) {
            return "";
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        return user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername();
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
}
