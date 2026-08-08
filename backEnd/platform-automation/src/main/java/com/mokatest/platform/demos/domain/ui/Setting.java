package com.mokatest.platform.demos.domain.ui;

import com.mokatest.platform.demos.domain.ui.uiEnum.ScreenshotConfig;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepErrorHandleStrategy;
import lombok.Data;

import java.io.Serializable;

@Data
public class Setting implements Serializable {
    private static final long serialVersionUID = 9222843759300727601L;

    private Integer isSetting; // 是否开启
    private Integer preExecuteWaitingTime; // 预执行等待时间
    private Integer waitingTimeAfterExecution; // 执行后等待时间
    private Integer timeout; // 超时时间
    private Integer pauseTimeout; // 调试暂停超时时间（分钟），0 表示不超时
    // 遇到错误的处理策略
    private StepErrorHandleStrategy errorHandlingStrategy;
    // 截图配置
    private ScreenshotConfig screenshotConfiguration;
    // 页面信息
    private String pageInformation;

    public Setting() {
        this.isSetting = 1;
        this.preExecuteWaitingTime = 0;
        this.waitingTimeAfterExecution = 0;
        this.timeout = 15;
        this.pauseTimeout = 30;
        this.errorHandlingStrategy = StepErrorHandleStrategy.STOP;
        this.screenshotConfiguration = ScreenshotConfig.SCREENSHOT;
        this.pageInformation = "";
    }
}