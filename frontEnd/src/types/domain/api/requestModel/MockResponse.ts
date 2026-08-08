import {RequestParameter} from "@/types/domain/api/requestModel/RequestParameter";
import {MockFieldRule} from "@/types/domain/api/requestModel/MockFieldRule";

/**
 * Mock 响应配置
 */
export class MockResponse {
    enabled?: boolean;
    statusCode?: number;
    headers?: RequestParameter[];
    body?: string;
    bodyMode?: 'RAW' | 'RULES';
    /** 字段规则根节点 */
    rules?: MockFieldRule;
    delayMs?: number;

    constructor() {
        this.enabled = false;
        this.statusCode = 200;
        this.headers = [];
        this.body = '';
        this.bodyMode = 'RAW';
        this.rules = new MockFieldRule(true);
        this.delayMs = 0;
    }
}
