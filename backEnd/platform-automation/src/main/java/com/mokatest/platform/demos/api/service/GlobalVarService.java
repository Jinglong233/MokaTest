package com.mokatest.platform.demos.api.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.api.domain.GlobalVar;
import com.mokatest.platform.demos.api.domain.dto.AddGlobalVarDTO;

/**
* @author: JingLong
* @description 针对表【global_var(全局参数表)】的数据库操作Service
* @createDate 2026-04-03 11:16:56
*/
public interface GlobalVarService extends IService<GlobalVar> {

    SaResult saveOrUpdate(AddGlobalVarDTO addGlobalVarDTO);

    SaResult getGlobalArgList(Integer teamId);

    SaResult deleteGlobalVar(Integer id);
}
