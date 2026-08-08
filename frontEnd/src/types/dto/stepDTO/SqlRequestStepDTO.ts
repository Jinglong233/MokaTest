import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";

/**
 * SQL 步骤DTO
 *
 * 与 API 请求步骤一致的副本模式：
 * - apiRequestId：仅用于追溯来源（可为空）
 * - apiConfig：完整的 SQL 接口配置副本（apiType=SQL，含 sqlConfig：SQL 语句、数据库连接、提取、断言等）
 * - 修改场景里的 SQL 步骤只影响当前场景的副本，不影响原始接口
 */
export class SqlRequestStepDTO extends StepBaseDTO {
    stepType: string = 'SQL';

    /**
     * 来源 SQL 接口ID（追溯用，可为空）
     */
    apiRequestId: number | null = null;

    /**
     * 接口名称（展示用）
     */
    apiName: string = '';

    /**
     * 完整的 SQL 接口配置副本
     * 包含：apiType=SQL、sqlConfig（sql / dbConnectionName / dbConfig / timeout / maxRows / params / sqlExtractions / sqlAssertions）
     */
    apiConfig: any = null;
}
