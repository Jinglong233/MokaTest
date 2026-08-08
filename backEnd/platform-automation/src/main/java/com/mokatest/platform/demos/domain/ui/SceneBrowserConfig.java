package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.TableField;
import com.mokatest.platform.demos.domain.ui.uiEnum.BrowserRunningType;
import com.mokatest.platform.demos.domain.ui.uiEnum.BrowserType;
import com.mokatest.platform.demos.domain.ui.uiEnum.DeviceType;
import com.mokatest.platform.demos.domain.ui.uiEnum.WindowModel;
import lombok.Data;

import java.io.Serializable;

/**
 * 场景运行浏览器配置类
 */
@Data
public class SceneBrowserConfig implements Serializable {
    /**
     * 浏览器类型
     */
    private Object browserType;

    /**
     * 运行模式（有头、无头）
     */
    private Object runningType;

    /**
     * 窗口大小
     */
    private String windowSize;

    /**
     * 设备类型
     */
    private DeviceType deviceType;

    /**
     * 窗口模式（最大化、指定尺寸）
     */
    private Object windowMode;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public SceneBrowserConfig() {
        this.windowSize = "2560x1440";
        this.windowMode = WindowModel.CUSTOMSIZE;
        this.deviceType = DeviceType.PC;
        this.runningType = BrowserRunningType.HEADLESS;
        this.browserType = BrowserType.CHROME;
    }
}