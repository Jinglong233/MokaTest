// for循环操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ConditionalRelationship} from "@/types/enum/condation/ConditionalRelationship";
import {ForCycleType} from "@/types/enum/cycle/ForCycleType";

export class ForStepDTO extends StepBaseDTO {
    stepType: string = 'FOR';
    // 循环方式
    cycleType: ForCycleType | string = 'TIMES';
    // 循环次数
    cycleTimes?: number = 1;
    // 循环文件
    cycleFile?: string = '';

}






