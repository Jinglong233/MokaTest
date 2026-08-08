import {ScreenshotConfig} from "@/types/enum/ScreenshotConfig";
import {StepErrorHandleStrategy} from "@/types/enum/StepErrorHandleStrategy";

export class Setting {

    isSetting?: number = 1; // 是否开启
    preExecuteWaitingTime?: number = 0; // 预执行等待时间
    waitingTimeAfterExecution?: number = 0; // 执行后等待时间
    timeout?: number = 15; // 超时时间
    // 遇到错误的处理策略
    errorHandlingStrategy?: StepErrorHandleStrategy = StepErrorHandleStrategy.STOP;
    // 截图配置
    screenshotConfiguration?: ScreenshotConfig = ScreenshotConfig.SCREENSHOT;
    // 页面信息
    pageInformation?: String = "";
}