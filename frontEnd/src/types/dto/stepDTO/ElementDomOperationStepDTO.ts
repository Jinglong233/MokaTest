// 元素DOM操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ElementDTO} from "@/types/dto/ElementDTO";
import {ElementDomOperationType} from "@/types/enum/element/ElementDomOperationType";
import {StylePriority} from "@/types/enum/element/StylePriority";

export class ElementDomOperationStepDTO extends StepBaseDTO {
    stepType: string = "ELEMENT_DOM_OPERATION"; // 步骤类型
    operationType: ElementDomOperationType | string = ElementDomOperationType.SET_ATTRIBUTE; // 操作类型
    element: ElementDTO = new ElementDTO(); // 目标元素

    // 属性操作
    attributeName: string = ""; // 属性名
    attributeValue: string = ""; // 属性值（空字符串也允许，支持 {{变量名}}）

    // 样式操作
    styleName: string = ""; // CSS属性名
    styleValue: string = ""; // CSS属性值（支持 {{变量名}}）
    stylePriority: StylePriority | string = StylePriority.NORMAL; // 样式优先级

    // 类名操作
    classNames: string[] = []; // CSS类名列表

    // 事件操作
    eventType: string = "input"; // 事件类型
    eventBubbles: boolean = true; // 是否冒泡
    eventCancelable: boolean = true; // 是否可取消
}
