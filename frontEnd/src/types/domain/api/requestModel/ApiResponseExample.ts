import { RequestParameter } from './RequestParameter';

/**
 * Swagger / OpenAPI 响应示例
 */
export interface ApiResponseExample {
  /** HTTP 状态码，如 200、400、500 */
  statusCode: number;

  /** 响应描述，如 "成功" */
  description?: string;

  /** 响应 content-type，如 application/json */
  contentType?: string;

  /** 响应头列表 */
  headers?: RequestParameter[];

  /** 响应体（RAW 文本） */
  body?: string;

  /** 响应体模式：RAW / RULES */
  bodyMode?: string;
}
