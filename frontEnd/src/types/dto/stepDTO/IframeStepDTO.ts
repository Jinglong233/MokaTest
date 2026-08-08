// iframe操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {SwitchIframeType} from "@/types/enum/iframe/SwitchIframeType";
import {ElementDTO} from "@/types/dto/ElementDTO";

export class IframeStepDTO extends StepBaseDTO {
    stepType: string = 'IFRAME';
    // 切换方式
    switchIframeType: SwitchIframeType | string = 'ID';
    // 目标元素
    element?: ElementDTO = new ElementDTO();
    url?: string = '';
    iframeName?: string = '';
    iframeIndex?: number = 0;
    iframeId?: string = '';
}






