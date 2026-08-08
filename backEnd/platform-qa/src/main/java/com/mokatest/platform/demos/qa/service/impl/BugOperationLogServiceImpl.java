package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.domain.BugOperationLog;
import com.mokatest.platform.demos.qa.mapper.BugOperationLogMapper;
import com.mokatest.platform.demos.qa.service.BugOperationLogService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class BugOperationLogServiceImpl extends ServiceImpl<BugOperationLogMapper, BugOperationLog> implements BugOperationLogService {

    @Override
    public SaResult listByBug(Integer bugId) {
        QueryWrapper<BugOperationLog> wrapper = new QueryWrapper<>();
        wrapper.eq("bug_id", bugId).orderByDesc("operate_time");
        List<BugOperationLog> list = baseMapper.selectList(wrapper);
        return SaResult.ok().setData(list);
    }

    @Override
    public void logOperation(Integer bugId, String fieldName, String oldValue, String newValue, Integer operatorId) {
        if (bugId == null || fieldName == null) {
            return;
        }
        BugOperationLog log = new BugOperationLog();
        log.setBugId(bugId);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setOperatorId(operatorId);
        log.setOperateTime(new Date());
        save(log);
    }
}
