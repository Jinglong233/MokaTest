// 拖拽元素

import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {Element} from '@/types/domain/Element';
import {ElementDTO} from "@/types/dto/ElementDTO";

export class DragElementStepDTO extends StepBaseDTO {
    stepType: string = 'DRAG'; // 步骤类型
    dragElement: ElementDTO = new ElementDTO(); // 被拖拽元素
    targetElement: ElementDTO = new ElementDTO(); // 目标元素
}