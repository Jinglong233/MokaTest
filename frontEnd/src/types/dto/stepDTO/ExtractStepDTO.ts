// 提取操作
import {ExtractType} from "@/types/enum/extract/ExtractType";
import {ElementExtractValueType} from "@/types/enum/extract/ElementExtractValueType";
import {PageExtractValueType} from "@/types/enum/extract/PageExtractValueType";
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ElementDTO} from "@/types/dto/ElementDTO";

// 提取操作
export class ExtractStepDTO extends StepBaseDTO {
    stepType: string = 'EXTRACT'; // 步骤类型
    variableName: string = ''; // 变量名
    extractType: ExtractType | string = "PAGE"; // 提取类型
    element?: ElementDTO = new ElementDTO(); // 提取的目标元素
    elementExtractType?: ElementExtractValueType | string = "TEXT"; // 元素提取类型
    elementAttribute?: string = "value";
    pageExtractType?: PageExtractValueType | string = "TITLE"; // 页面提取类型
}

