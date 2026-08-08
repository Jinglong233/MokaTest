import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {WaitType} from "@/types/enum/WaitType";

// 等待操作
export class WaitStepDTO extends StepBaseDTO {

    stepType: string = 'WAIT';

    // 等待类型
    waitType: WaitType | string = 'TIME';
    waitTime: number = 0 // 等待时间
}