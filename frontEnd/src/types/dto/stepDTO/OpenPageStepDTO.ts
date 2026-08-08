// 打开页面操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";

export class OpenPageStepDTO extends StepBaseDTO {
    stepType: string = 'OPEN_PAGE'; // 步骤类型
    url: string = ''; // 页面地址
    recover: number = 0;// 是否覆盖输入

}
