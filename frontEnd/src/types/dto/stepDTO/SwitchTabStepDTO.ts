import {SwitchTabMode} from "@/types/enum/browser/SwitchTabMode";
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";

// 切换页签
export class SwitchTabStepDTO extends StepBaseDTO {
    stepType: string = "SWITCH_TAB";
    switchTabMode: SwitchTabMode | string = "FIRST"; // 关闭页面模式
    customIndex?: number = 0; // 自定义关闭页面索引
}