import axios from "axios";
import {ResponseSchema} from "@/types/domain/api/requestModel/ResponseSchema";

/**
 * 按响应定义预览生成的 Mock 响应体
 */
export function previewResponseSchema(responseSchema: ResponseSchema) {
    return axios.post<string>('/api/mock/previewSchema', responseSchema);
}

/**
 * 根据 Mock/Template 表达式生成单个值
 */
export function generateMock(expression: string) {
    return axios.post<string>('/api/mock/generate', { expression });
}
