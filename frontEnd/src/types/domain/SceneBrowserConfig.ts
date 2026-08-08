import {BrowserRunningType} from "@/types/enum/BrowserRunningType";
import {BrowserType} from "@/types/enum/BrowserType";
import {WindowModel} from "@/types/enum/WindowModel";

/**
 * 场景运行浏览器配置类
 */
export class SceneBrowserConfig {
    /**
     * 浏览器类型
     */
    browserType?: Object = "2560x1440";

    /**
     * 运行模式（有头、无头）
     */
    runningType?: Object = WindowModel.CUSTOMSIZE;

    /**
     * 窗口大小
     */
    windowSize?: string | DeviceType = DeviceType.PC;

    /**
     * 设备类型
     */
    deviceType?: string | DeviceType = BrowserRunningType.HEADLESS;

    /**
     * 窗口模式（最大化、指定尺寸）
     */
    windowMode?: Object = BrowserType.CHROME;
}