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

        QueryWrapper<SysOperationLog> wrapper = buildQueryWrapper(finalQuery);
        // 列表不返回大字段（requestParams/userAgent），详情单独查
        wrapper.select(SysOperationLog.class, field ->
                !"request_params".equals(field.getColumn())
                        && !"user_agent".equals(field.getColumn()));

        IPage<SysOperationLog> result = baseMapper.selectPage(page, wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return SaResult.ok().setData(data);
    }

    @Override
    public void delete(Long id) {
        baseMapper.deleteById(id);
    }

    @Override
    public void batchDelete(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        baseMapper.deleteBatchIds(ids);
    }

    @Override
    public void clear() {
        baseMapper.delete(new QueryWrapper<SysOperationLog>().isNotNull("id"));
    }

    @Override
    public void export(OperationLogQueryDTO query, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        final OperationLogQueryDTO finalQuery = query != null ? query : new OperationLogQueryDTO();
        QueryWrapper<SysOperationLog> wrapper = buildQueryWrapper(finalQuery);
        // 导出不带大字段，避免单条日志的描述/请求参数撑爆 Excel 单元格
        wrapper.select(SysOperationLog.class, field ->
                !"request_params".equals(field.getColumn())
                        && !"user_agent".equals(field.getColumn())
                        && !"description".equals(field.getColumn()));
        java.util.List<SysOperationLog> list = baseMapper.selectList(wrapper);

        java.util.List<java.util.List<Object>> rows = new java.util.ArrayList<>();
        rows.add(java.util.Arrays.asList("操作时间", "操作人", "模块", "操作类型", "对象类型", "对象", "结果", "响应信息", "耗时(ms)", "IP"));
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (SysOperationLog log : list) {
            java.util.List<Object> row = new java.util.ArrayList<>();
            row.add(log.getOperateTime() != null ? sdf.format(log.getOperateTime()) : "");
            row.add(log.getOperatorName() != null ? log.getOperatorName() : "");
            row.add(log.getModule() != null ? log.getModule() : "");
            row.add(log.getOperateType() != null ? log.getOperateType() : "");
            row.add(log.getTargetType() != null ? log.getTargetType() : "");
            row.add(log.getTargetName() != null ? log.getTargetName() : "");
            row.add(log.getResponseCode() != null && log.getResponseCode() == 200 ? "成功" : "失败");
            row.add(log.getResponseMsg() != null ? log.getResponseMsg() : "");
            row.add(log.getDurationMs() != null ? log.getDurationMs() : "");
            row.add(log.getIp() != null ? log.getIp() : "");
            rows.add(row);
        }

        cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter();
        writer.write(rows, true);
        writer.autoSizeColumnAll();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=operation_log_export.xlsx");
        writer.flush(response.getOutputStream());
        writer.close();
    }

    private QueryWrapper<SysOperationLog> buildQueryWrapper(OperationLogQueryDTO query) {
        QueryWrapper<SysOperationLog> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(query.getModule())) {
            wrapper.eq("module", query.getModule());
        }
        if (StringUtils.hasText(query.getOperateType())) {
            wrapper.eq("operate_type", query.getOperateType());
        }
        if (StringUtils.hasText(query.getTargetType())) {
            wrapper.eq("target_type", query.getTargetType());
        }
        if (query.getOperatorId() != null) {
            wrapper.eq("operator_id", query.getOperatorId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like("description", query.getKeyword())
                    .or()
                    .like("target_name", query.getKeyword()));
        }
        if ("SUCCESS".equals(query.getStatus())) {
            wrapper.eq("response_code", 200);
        } else if ("FAIL".equals(query.getStatus())) {
            // SQL 中 != 200 天然排除 NULL（无响应码的记录不算失败）
            wrapper.ne("response_code", 200);
        }
        if (StringUtils.hasText(query.getIp())) {
            wrapper.like("ip", query.getIp());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge("operate_time", query.getStartTime());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le("operate_time", query.getEndTime());
        }
        wrapper.orderByDesc("operate_time");
        return wrapper;
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
