// 断言操作
import {AssertType} from "@/types/enum/condation/AssertType";
import {AssertRelationship} from "@/types/enum/condation/AssertRelationship";
import {PageAttribute} from "@/types/enum/page/PageAttribute";

import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ElementDTO} from "@/types/dto/ElementDTO";

export class AssertStepDTO extends StepBaseDTO {
    stepType: string = 'ASSERT';
    assertType: AssertType | string = 'ELEMENT_EXIST' // 断言类型
    element?: ElementDTO = new ElementDTO(); // 断言元素
    assertText?: string = ''; // 断言文本
    elementAttribute?: string = "value"; // 断言元素属性
    assertRelationship?: AssertRelationship | string = "EQUALS"; // 断言关系
    exceptValue?: string; // 期望值
    pageAttribute?: PageAttribute | string = "PAGE_TITLE"; // 断言元素属性
}






