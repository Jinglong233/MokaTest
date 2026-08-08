// 悬停操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ElementDTO} from "@/types/dto/ElementDTO";

export class HoverStepDTO extends StepBaseDTO {
    stepType: string = "HOVER"; // 步骤类型
    element: ElementDTO = new ElementDTO(); // 元素
}