package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.TestCaseReview;
import com.mokatest.platform.demos.qa.domain.TestCaseReviewCase;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseReviewCaseMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseReviewItemMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseReviewMapper;
import com.mokatest.platform.demos.qa.service.TestCaseOperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 重新评审（resetCase）单元测试：状态回退 / 权限校验 / 完结单重开
 */
class TestCaseReviewResetCaseTest {

    private TestCaseReviewServiceImpl service;
    private TestCaseReviewMapper reviewMapper;
    private TestCaseReviewCaseMapper reviewCaseMapper;
    private TestCaseReviewItemMapper reviewItemMapper;
    private TestCaseMapper testCaseMapper;
    private TestCaseOperationLogService operationLogService;
    private MockedStatic<StpUtil> stpMock;

    @BeforeEach
    void setUp() {
        reviewMapper = mock(TestCaseReviewMapper.class);
        reviewCaseMapper = mock(TestCaseReviewCaseMapper.class);
        reviewItemMapper = mock(TestCaseReviewItemMapper.class);
        testCaseMapper = mock(TestCaseMapper.class);
        operationLogService = mock(TestCaseOperationLogService.class);
        service = new TestCaseReviewServiceImpl(
                reviewCaseMapper, reviewItemMapper, null, null, testCaseMapper,
                null, null, null, operationLogService, null, null);
        ReflectionTestUtils.setField(service, "baseMapper", reviewMapper);
        stpMock = Mockito.mockStatic(StpUtil.class);
        stpMock.when(StpUtil::getLoginIdAsInt).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        stpMock.close();
    }

    private TestCaseReview review(int creatorId, String status) {
        TestCaseReview r = new TestCaseReview();
        r.setId(100);
        r.setCreatorId(creatorId);
        r.setStatus(status);
        r.setReviewCode("CR-100");
        return r;
    }

    private TestCaseReviewCase reviewCase(String finalResult) {
        TestCaseReviewCase rc = new TestCaseReviewCase();
        rc.setReviewId(100);
        rc.setTestCaseId(9);
        rc.setFinalResult(finalResult);
        return rc;
    }

    @Test
    void resetSuccess() {
        when(reviewMapper.selectById(100)).thenReturn(review(1, "REVIEWING"));
        when(reviewCaseMapper.selectOne(any())).thenReturn(reviewCase("PASS"));
        TestCase tc = new TestCase();
        tc.setId(9);
        tc.setStatus("REVIEWED");
        when(testCaseMapper.selectById(9)).thenReturn(tc);

        SaResult res = service.resetCase(100, 9);

        assertEquals(200, res.getCode());
        // final_result 回退 PENDING
        verify(reviewCaseMapper).updateById(argThat((TestCaseReviewCase rc) -> "PENDING".equals(rc.getFinalResult())));
        // 明细重置（结论 PENDING + 意见/时间清空）
        verify(reviewItemMapper).update(isNull(), any(UpdateWrapper.class));
        // 用例状态拨回 REVIEWING
        verify(testCaseMapper).updateById(argThat((TestCase t) -> "REVIEWING".equals(t.getStatus())));
    }

    @Test
    void nonCreatorRejected() {
        when(reviewMapper.selectById(100)).thenReturn(review(2, "REVIEWING"));

        SaResult res = service.resetCase(100, 9);

        assertNotEquals(200, res.getCode());
        verifyNoInteractions(reviewCaseMapper, reviewItemMapper, testCaseMapper);
    }

    @Test
    void cancelledReviewRejected() {
        when(reviewMapper.selectById(100)).thenReturn(review(1, "CANCELLED"));

        SaResult res = service.resetCase(100, 9);

        assertNotEquals(200, res.getCode());
        verifyNoInteractions(reviewCaseMapper, reviewItemMapper, testCaseMapper);
    }

    @Test
    void completedReviewReopened() {
        TestCaseReview r = review(1, "COMPLETED");
        when(reviewMapper.selectById(100)).thenReturn(r);
        when(reviewCaseMapper.selectOne(any())).thenReturn(reviewCase("REJECT"));
        TestCase tc = new TestCase();
        tc.setId(9);
        tc.setStatus("REVIEWED");
        when(testCaseMapper.selectById(9)).thenReturn(tc);

        SaResult res = service.resetCase(100, 9);

        assertEquals(200, res.getCode());
        // 完结单自动重开
        assertEquals("REVIEWING", r.getStatus());
        assertNull(r.getResultSummary());
        verify(reviewMapper).updateById(r);
    }

    @Test
    void caseNotInReviewRejected() {
        when(reviewMapper.selectById(100)).thenReturn(review(1, "REVIEWING"));
        when(reviewCaseMapper.selectOne(any())).thenReturn(null);

        SaResult res = service.resetCase(100, 9);

        assertNotEquals(200, res.getCode());
        verify(reviewCaseMapper, never()).updateById(any(TestCaseReviewCase.class));
    }
}
