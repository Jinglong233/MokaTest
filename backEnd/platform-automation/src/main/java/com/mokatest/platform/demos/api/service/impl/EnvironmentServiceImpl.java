package com.mokatest.platform.demos.api.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.api.domain.Environment;
import com.mokatest.platform.demos.api.domain.dto.AddEnvDTO;
import com.mokatest.platform.demos.api.mapper.EnvironmentMapper;
import com.mokatest.platform.demos.api.service.EnvironmentService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * @author: JingLong
 * @description 针对表【environment(环境配置表)】的数据库操作Service实现
 * @createDate 2026-04-03 11:16:56
 */
@Service
public class EnvironmentServiceImpl extends ServiceImpl<EnvironmentMapper, Environment>
        implements EnvironmentService {

    @Resource
    private EnvironmentMapper environmentMapper;

    @Override
    public SaResult saveOrUpdate(AddEnvDTO addEnvDTO) {
        if (addEnvDTO == null) return SaResult.error("缺少参数");
        if (addEnvDTO.getId() != null) {
            addEnvDTO.setUpdateTime(new Date());
            addEnvDTO.setUpdateUserId(StpUtil.getLoginIdAsInt());
        } else {
            addEnvDTO.setCreateTime(new Date());
            addEnvDTO.setCreateUserId(StpUtil.getLoginIdAsInt());
        }
        Environment environment = new Environment();
        BeanUtil.copyProperties(addEnvDTO, environment);
        return environmentMapper.insertOrUpdate(environment) ? SaResult.ok().setData(environment.getId()) : SaResult.error();
    }

    @Override
    public SaResult getEnvList(Integer teamId) {
        if (teamId == null) return SaResult.error("缺少团队id");
        QueryWrapper<Environment> environmentQueryWrapper = new QueryWrapper<>();
        environmentQueryWrapper.eq("team_id", teamId);
        return SaResult.ok().setData(environmentMapper.selectList(environmentQueryWrapper));
    }

    @Override
    public SaResult copy(Integer targetId) {
        if (targetId == null) return SaResult.error("缺少目标id");
        Environment environment = environmentMapper.selectById(targetId);
        if (environment == null) return SaResult.error("目标环境不存在");
        environment.setEnvName(environment.getEnvName() + "_副本");
        environment.setId(null);
        environment.setCreateUserId(StpUtil.getLoginIdAsInt());
        environment.setCreateTime(new Date());
        environment.setUpdateTime(null);
        environment.setUpdateUserId(null);
        int insert = environmentMapper.insert(environment);
        if (insert <= 0) {
            return SaResult.error("复制失败");
        }
        return SaResult.ok("复制成功").setData(environment.getId());
    }

    @Override
    @Transactional
    public SaResult deleteEnv(Integer id) {
        if (id == null) return SaResult.error("缺少参数");
        Environment environment = environmentMapper.selectById(id);
        if (environment == null) return SaResult.error("环境不存在");
        environment.setDeletedAt(new Date());
        boolean success = environmentMapper.deleteById(environment) > 0;
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }
}




