package com.mokatest.platform.demos.operationlog.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.mapper.SysOperationLogMapper;
import com.mokatest.platform.demos.operationlog.domain.SysOperationLog;
import com.mokatest.platform.demos.operationlog.dto.OperationLogQueryDTO;
import com.mokatest.platform.demos.operationlog.service.SysOperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统操作日志 Service 实现
 */
@Slf4j
@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog> implements SysOperationLogService {

    @Override
    @Async("operationLogExecutor")
    public void asyncSave(SysOperationLog log) {
        if (log == null) {
            return;
        }
        try {
            baseMapper.insert(log);
        } catch (Exception e) {
            // 日志写入异常不影响主业务，仅打印错误
            SysOperationLogServiceImpl.log.error("操作日志异步保存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public SaResult list(OperationLogQueryDTO query) {
        final OperationLogQueryDTO finalQuery = query != null ? query : new OperationLogQueryDTO();
        int pageNum = finalQuery.getPageNum() != null ? finalQuery.getPageNum() : 1;
        int pageSize = finalQuery.getPageSize() != null ? finalQuery.getPageSize() : 20;
        IPage<SysOperationLog> page = new Page<>(pageNum, pageSize);

        QueryWrapper<SysOperationLog> wrapper = new QueryWrapper<>();
        // 列表不返回大字段（requestParams/userAgent），详情单独查
        wrapper.select(SysOperationLog.class, field ->
                !"request_params".equals(field.getColumn())
                        && !"user_agent".equals(field.getColumn()));
        if (StringUtils.hasText(finalQuery.getModule())) {
            wrapper.eq("module", finalQuery.getModule());
        }
        if (StringUtils.hasText(finalQuery.getOperateType())) {
            wrapper.eq("operate_type", finalQuery.getOperateType());
        }
        if (StringUtils.hasText(finalQuery.getTargetType())) {
            wrapper.eq("target_type", finalQuery.getTargetType());
        }
        if (finalQuery.getOperatorId() != null) {
            wrapper.eq("operator_id", finalQuery.getOperatorId());
        }
        if (StringUtils.hasText(finalQuery.getKeyword())) {
            wrapper.and(w -> w.like("description", finalQuery.getKeyword())
                    .or()
                    .like("target_name", finalQuery.getKeyword()));
        }
        if (StringUtils.hasText(finalQuery.getStartTime())) {
            wrapper.ge("operate_time", finalQuery.getStartTime());
        }
        if (StringUtils.hasText(finalQuery.getEndTime())) {
            wrapper.le("operate_time", finalQuery.getEndTime());
        }
        wrapper.orderByDesc("operate_time");

        IPage<SysOperationLog> result = baseMapper.selectPage(page, wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return SaResult.ok().setData(data);
    }

    @Override
    public SaResult detail(Long id) {
        if (id == null) {
            return SaResult.error("id 不能为空");
        }
        SysOperationLog log = baseMapper.selectById(id);
        if (log == null) {
            return SaResult.error("日志不存在");
        }
        return SaResult.ok().setData(log);
    }
}
