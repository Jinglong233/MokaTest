// 步骤设置

import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";
import {ExtractStepDTO} from "@/types/dto/stepDTO/ExtractStepDTO";
import {StepErrorHandleStrategy} from "@/types/enum/StepErrorHandleStrategy";
import {ScreenshotConfig} from "@/types/enum/ScreenshotConfig";

export class Setting {
    isSetting: number = 0; // 是否开启
    preExecuteWaitingTime: number = 0; // 预执行等待时间
    waitingTimeAfterExecution: number = 0; // 执行后等待时间
    timeout: number = 0; // 超时时间
    // 遇到错误的处理策略
    errorHandlingStrategy: StepErrorHandleStrategy | string = "IGNORE";
    // 截图配置
    screenshotConfiguration: ScreenshotConfig | string = "NOT_SCREENSHOT";
    // 页面信息
    pageInformation: string = '';
}

export class SettingDTO {
    setting: Setting = new Setting();
    assertList: AssertStepDTO[] = [];
    extractList: ExtractStepDTO[] = []
}