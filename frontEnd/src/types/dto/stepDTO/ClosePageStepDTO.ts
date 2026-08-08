// 关闭页面操作
import {ClosePageMode} from "@/types/enum/browser/ClosePageMode";
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";

export class ClosePageStepDTO extends StepBaseDTO {

    stepType: string = "CLOSE_PAGE";
    closePageMode: ClosePageMode | string = "CURRENT"; // 关闭页面模式
    customIndex?: number = 0; // 自定义关闭页面索引
}
