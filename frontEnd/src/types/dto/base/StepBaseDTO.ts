import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";
import {ExtractStepDTO} from "@/types/dto/stepDTO/ExtractStepDTO";
import {Setting} from "@/types/dto/SettingDTO";
import {StepType} from "@/types/enum/StepType";

export class StepBaseDTO {
    parentId: number = 0;// 父节点id
    stepName: string = '';// 步骤名称

    stepType: StepType | string = '';// 步骤类型
    assertList?: AssertStepDTO[] = []; // 断言列表
    extractList?: ExtractStepDTO[] = []; // 提取列表
    setting?: Setting = new Setting();// 步骤设置
}