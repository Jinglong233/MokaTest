package com.mokatest.platform.demos.api.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.api.domain.Environment;
import com.mokatest.platform.demos.api.domain.dto.AddEnvDTO;

/**
 * @author: JingLong
 * @description 针对表【environment(环境配置表)】的数据库操作Service
 * @createDate 2026-04-03 11:16:56
 */
public interface EnvironmentService extends IService<Environment> {

    SaResult saveOrUpdate(AddEnvDTO addEnvDTO);

    SaResult getEnvList(Integer teamId);

    SaResult copy(Integer targetId);

    SaResult deleteEnv(Integer id);
}
