// 文件上传操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ElementDTO} from "@/types/dto/ElementDTO";

export class UploadFileStepDTO extends StepBaseDTO {
    stepType: string = "FILE_UPLOAD"; // 步骤类型
    element: ElementDTO = new ElementDTO(); // 文件输入框元素
    fileIds: string[] = []; // 上传文件的fileId列表
}
