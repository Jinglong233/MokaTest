package com.mokatest.platform.demos.domain.ui;

import com.mokatest.platform.demos.api.domain.requestModel.ApiSceneConfig;
import com.mokatest.platform.demos.api.domain.requestModel.RequestExecuteInfo;
import lombok.Data;

/**
 * 场景配置
 */
@Data
public class SceneSetting {
    private SceneBrowserConfig sceneBrowserConfig;
    private Setting setting;
    /**
     * API场景环境配置
     * 用于API自动化场景，存储引用的环境信息（环境ID、服务地址等）
     */
    private RequestExecuteInfo apiEnvConfig;
    /**
     * API场景级配置
     * 作用域为当前API场景，优先级：接口配置 > 场景配置 > 环境配置
     */
    private ApiSceneConfig apiSceneConfig;

    public SceneSetting() {
        this.sceneBrowserConfig = new SceneBrowserConfig();
        this.setting = new Setting();
    }
}

