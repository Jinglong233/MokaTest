import {DialogOperationType} from "@/types/enum/dialog/DialogOperationType";
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";

export class DialogStepDTO extends StepBaseDTO {
    stepType: string = 'DIALOG';
    // 对话框操作方式
    dialogOperationType: DialogOperationType | string = 'ACCEPT';

    dialogMessage: string = '';
}