// 点击操作
import {ClickType} from "@/types/enum/click/ClickType";
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ElementDTO} from "@/types/dto/ElementDTO";

export class ClickStepDTO extends StepBaseDTO {
    stepType: string = "CLICK"; // 步骤类型
    clickType: ClickType | string = "SINGLE_CLICK"; // 点击类型
    element: ElementDTO = new ElementDTO(); // 元素
}