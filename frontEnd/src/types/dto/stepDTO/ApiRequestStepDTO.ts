import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";

/**
 * API请求步骤DTO
 *
 * 说明：场景中的API步骤使用副本模式（不是引用）。
 * - apiRequestId：仅用于追溯来源（可为空）
 * - apiConfig：完整的API配置副本，包含请求方法、路径、参数、Body、断言、提取等
 * - 修改场景里的API步骤只影响当前场景的副本，不影响原始接口/用例
 */
export class ApiRequestStepDTO extends StepBaseDTO {
    stepType: string = 'API_REQUEST';

    /**
     * 来源API接口/用例ID（追溯用，可为空）
     */
    apiRequestId: number | null = null;

    /**
     * API名称（展示用）
     */
    apiName: string = '';

    /**
     * 完整的API配置副本
     * 包含：requestMethod、requestPath、requestHeader、body、断言、提取、脚本等
     */
    apiConfig: any = null;
}
