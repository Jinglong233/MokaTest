// 键盘操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {KeyboardInputType} from "@/types/enum/keyboard/KeyboardInputType";
import {KeyboardKey} from "@/types/enum/keyboard/KeyboardKey";
import {ElementDTO} from "@/types/dto/ElementDTO";

export class KeyboardStepDTO extends StepBaseDTO {
    stepType: string = 'KEYBOARD'; // 步骤类型
    element: ElementDTO = new ElementDTO(); // 操作元素
    inputType: KeyboardInputType | string = 'NORMAL'; // 输入类型

    keyboardKey?: KeyboardKey | string = "ENTER"; // 按键
    inputValue?: string = ''; // 输入内容
    isAdditional?: number = 0; // 是否追加输入
}