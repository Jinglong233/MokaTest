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
    public SaResult list(String operation, String status, String keyword,
                         String startTime, String endTime, Integer pageNum, Integer pageSize) {
        int page = pageNum != null ? pageNum : 1;
        int size = pageSize != null ? pageSize : 20;
        IPage<SysLoginLog> pageObj = new Page<>(page, size);

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
        if (StringUtils.hasText(startTime)) {
            wrapper.ge("operate_time", startTime);
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le("operate_time", endTime);
        }
        wrapper.orderByDesc("operate_time");

        IPage<SysLoginLog> result = baseMapper.selectPage(pageObj, wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return SaResult.ok().setData(data);
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
