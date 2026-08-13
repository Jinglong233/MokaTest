package com.mokatest.platform.demos.operationlog.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.mapper.SysLoginLogMapper;
import com.mokatest.platform.demos.operationlog.domain.SysLoginLog;
import com.mokatest.platform.demos.operationlog.service.SysLoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录日志 Service 实现
 */
@Slf4j
@Service
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog> implements SysLoginLogService {

    private final IpRegionService ipRegionService;

    public SysLoginLogServiceImpl(IpRegionService ipRegionService) {
        this.ipRegionService = ipRegionService;
    }

    @Override
    @Async("operationLogExecutor")
    public void asyncSave(SysLoginLog log) {
        if (log == null) {
            return;
        }
        try {
            baseMapper.insert(log);
        } catch (Exception e) {
            // 日志写入异常不影响主业务
            SysLoginLogServiceImpl.log.error("登录日志异步保存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void record(String operation, Long userId, String username, String nickname, boolean success, String message) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setOperation(operation);
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setNickname(nickname);
            entity.setStatus(success ? "SUCCESS" : "FAIL");
            entity.setMessage(message);
            entity.setOperateTime(new Date());

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = getClientIp(request);
                entity.setIp(ip);
                entity.setIpRegion(ipRegionService.resolve(ip));
                String ua = request.getHeader("User-Agent");
                if (ua != null && ua.length() > 500) {
                    ua = ua.substring(0, 500);
                }
                entity.setUserAgent(ua);
            }
            asyncSave(entity);
        } catch (Exception e) {
            log.error("登录日志记录失败", e);
        }
    }

    @Override
    public SaResult list(String operation, String status, String keyword, String ip,
                         String startTime, String endTime, Integer pageNum, Integer pageSize) {
        int page = pageNum != null ? pageNum : 1;
        int size = pageSize != null ? pageSize : 20;
        IPage<SysLoginLog> pageObj = new Page<>(page, size);

        QueryWrapper<SysLoginLog> wrapper = buildQueryWrapper(operation, status, keyword, ip, startTime, endTime);
        IPage<SysLoginLog> result = baseMapper.selectPage(pageObj, wrapper);
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
        baseMapper.delete(new QueryWrapper<SysLoginLog>().isNotNull("id"));
    }

    @Override
    public void export(String operation, String status, String keyword, String ip,
                       String startTime, String endTime, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        QueryWrapper<SysLoginLog> wrapper = buildQueryWrapper(operation, status, keyword, ip, startTime, endTime);
        java.util.List<SysLoginLog> list = baseMapper.selectList(wrapper);

        java.util.List<java.util.List<Object>> rows = new java.util.ArrayList<>();
        rows.add(java.util.Arrays.asList("时间", "用户名", "昵称", "操作类型", "状态", "失败原因", "IP", "归属地", "User-Agent"));
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (SysLoginLog log : list) {
            java.util.List<Object> row = new java.util.ArrayList<>();
            row.add(log.getOperateTime() != null ? sdf.format(log.getOperateTime()) : "");
            row.add(log.getUsername() != null ? log.getUsername() : "");
            row.add(log.getNickname() != null ? log.getNickname() : "");
            row.add("LOGOUT".equals(log.getOperation()) ? "登出" : "登录");
            row.add("SUCCESS".equals(log.getStatus()) ? "成功" : "失败");
            row.add(log.getMessage() != null ? log.getMessage() : "");
            row.add(log.getIp() != null ? log.getIp() : "");
            row.add(log.getIpRegion() != null ? log.getIpRegion() : "");
            row.add(log.getUserAgent() != null ? log.getUserAgent() : "");
            rows.add(row);
        }

        cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter();
        writer.write(rows, true);
        writer.autoSizeColumnAll();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=login_log_export.xlsx");
        writer.flush(response.getOutputStream());
        writer.close();
    }

    private QueryWrapper<SysLoginLog> buildQueryWrapper(String operation, String status, String keyword, String ip,
                                                        String startTime, String endTime) {
        QueryWrapper<SysLoginLog> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(operation)) {
            wrapper.eq("operation", operation);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("username", keyword).or().like("nickname", keyword));
        }
        if (StringUtils.hasText(ip)) {
            wrapper.like("ip", ip);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge("operate_time", startTime);
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le("operate_time", endTime);
        }
        wrapper.orderByDesc("operate_time");
        return wrapper;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
