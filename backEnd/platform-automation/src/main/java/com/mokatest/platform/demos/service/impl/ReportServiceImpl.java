package com.mokatest.platform.demos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.uiEnum.task.TaskType;
import com.mokatest.platform.demos.domain.ui.Report;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.ReportQueryDTO;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.ReportMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.service.ReportService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @author: JingLong
 * @description 针对表【report】的数据库操作Service实现
 * @createDate 2025-09-24 19:59:50
 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {


    @Resource
    private ReportMapper reportMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 填充报告列表的执行者显示名称。
     * 规则：
     *   定时任务({@link TaskType#TIMING}) → 显示"定时任务"
     *   普通任务({@link TaskType#NORMAL}) → 显示执行人昵称，未找到昵称则显示用户名，再未找到则显示用户ID
     *
     * @param reports 报告列表
     */
    private void fillExecutionUserName(List<Report> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        for (Report report : reports) {
            if (TaskType.TIMING.name().equals(report.getTaskType())) {
                // 定时任务：统一显示"定时任务"，不暴露系统用户ID
                report.setExecutionUserName("定时任务");
            } else if (TaskType.NORMAL.name().equals(report.getTaskType())) {
                // 立即执行：查询用户表获取可读名称
                if (StringUtils.isNotBlank(report.getExecutionUserId())) {
                    try {
                        User user = userMapper.selectById(Long.valueOf(report.getExecutionUserId()));
                        if (user != null && StringUtils.isNotBlank(user.getNickname())) {
                            report.setExecutionUserName(user.getNickname());
                        } else if (user != null && StringUtils.isNotBlank(user.getUsername())) {
                            report.setExecutionUserName(user.getUsername());
                        } else {
                            // 用户未找到， fallback 显示用户ID
                            report.setExecutionUserName(report.getExecutionUserId());
                        }
                    } catch (NumberFormatException e) {
                        // executionUserId 不是数字，直接显示原值
                        report.setExecutionUserName(report.getExecutionUserId());
                    }
                }
            }
        }
    }

    @Override
    public List<Report> allReport(Integer projectId) {
        if (projectId == null) {
            throw new ParamIsEmptyException("缺少必要参数");
        }
        QueryWrapper<Report> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        queryWrapper.orderByDesc("create_time");
        // 报告列表按项目开放，同一项目下所有成员均可查看全部报告
        List<Report> reportList = reportMapper.selectList(queryWrapper);
        // 填充执行者显示名称（定时任务 / 具体人名）
        fillExecutionUserName(reportList);
        return reportList;
    }

    @Override
    public Report getReportDetail(Integer reportId) {
        Report report = reportMapper.selectById(reportId);
        return report;
    }

    @Override
    public Page<Report> reportPageList(ReportQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new ParamIsEmptyException("缺少必要参数");
        }
        // 创建分页对象
        Page<Report> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 创建查询条件
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Report::getProjectId, queryDTO.getProjectId());

        // 添加查询条件
        if (StringUtils.isNotBlank(queryDTO.getReportName())) {
            queryWrapper.like(Report::getReportName, queryDTO.getReportName());
        }


        if (StringUtils.isNotBlank(queryDTO.getTaskType())) {
            queryWrapper.eq(Report::getTaskType, TaskType.valueOf(queryDTO.getTaskType()));
        }

        if (queryDTO.getStatus() != null) {
            queryWrapper.eq(Report::getStatus, queryDTO.getStatus());
        }

        if (StringUtils.isNotBlank(queryDTO.getPlanName())) {
            queryWrapper.like(Report::getPlanName, queryDTO.getPlanName());
        }

        // 场景列表
        if (queryDTO.getScenes() != null) {
            queryWrapper.like(Report::getScenes, queryDTO.getScenes());
        }


        // 添加排序
        queryWrapper.orderByDesc(Report::getCreateTime);
        // 报告列表按项目开放，同一项目下所有成员均可查看全部报告，不再强制按当前登录用户过滤

        // 执行分页查询
        Page<Report> resultPage = baseMapper.selectPage(page, queryWrapper);
        // 填充执行者显示名称（定时任务 / 具体人名）
        fillExecutionUserName(resultPage.getRecords());
        return resultPage;
    }

    @Override
    public Boolean deleteReport(Integer reportId) {
        if (reportId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            return true;
        }
        // 仅允许删除已完成的报告
        if (report.getStatus() == null || report.getStatus() != 1) {
            throw new RuntimeException("仅允许删除已完成的报告");
        }
        report.setDeletedAt(new Date());
        return reportMapper.deleteById(report) > 0;
    }
}




