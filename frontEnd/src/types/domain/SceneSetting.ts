import {Setting} from "@/types/domain/Setting";
import {SceneBrowserConfig} from "@/types/domain/SceneBrowserConfig";

/**
 * 场景配置
 */
export class SceneSetting {
    sceneBrowserConfig?: SceneBrowserConfig = new SceneBrowserConfig();
    setting?: Setting = new Setting();
}

