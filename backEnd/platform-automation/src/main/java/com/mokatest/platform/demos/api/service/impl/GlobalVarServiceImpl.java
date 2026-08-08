package com.mokatest.platform.demos.api.service.impl;

import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.api.domain.GlobalVar;
import com.mokatest.platform.demos.api.domain.dto.AddGlobalVarDTO;
import com.mokatest.platform.demos.api.mapper.GlobalVarMapper;
import com.mokatest.platform.demos.api.service.GlobalVarService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * @author: JingLong
 * @description 针对表【global_var(全局参数表)】的数据库操作Service实现
 * @createDate 2026-04-03 11:16:56
 */
@Service
public class GlobalVarServiceImpl extends ServiceImpl<GlobalVarMapper, GlobalVar>
        implements GlobalVarService {

    @Resource
    private GlobalVarMapper globalVarMapper;

    @Override
    public SaResult saveOrUpdate(AddGlobalVarDTO addGlobalVarDTO) {
        if (addGlobalVarDTO == null) return SaResult.error("缺少参数");
        GlobalVar globalVar = new GlobalVar();
        BeanUtil.copyProperties(addGlobalVarDTO, globalVar);
        return globalVarMapper.insertOrUpdate(globalVar) ? SaResult.ok("成功").setData(globalVar.getId()) : SaResult.error("失败");
    }

    @Override
    public SaResult getGlobalArgList(Integer teamId) {
        if (teamId == null) return SaResult.error("缺少团队id");
        QueryWrapper<GlobalVar> globalVarQueryWrapper = new QueryWrapper<>();
        globalVarQueryWrapper.eq("team_id", teamId);
        return SaResult.ok("成功").setData(globalVarMapper.selectList(globalVarQueryWrapper));
    }

    @Override
    @Transactional
    public SaResult deleteGlobalVar(Integer id) {
        if (id == null) return SaResult.error("缺少参数");
        GlobalVar globalVar = globalVarMapper.selectById(id);
        if (globalVar == null) return SaResult.error("全局变量不存在");
        globalVar.setDeletedAt(new Date());
        boolean success = globalVarMapper.deleteById(globalVar) > 0;
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }
}




