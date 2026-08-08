import {RequestParameter} from "@/types/domain/api/requestModel/RequestParameter";
import {AssertParameter} from "@/types/domain/api/requestModel/AssertParameter";

/**
 * API 场景级配置
 *
 * 作用域为当前 API 场景，该场景下所有 API 步骤共享。
 * 优先级：接口配置 > 场景配置 > 环境配置 > 全局配置
 */
export class ApiSceneConfig {

    /**
     * 场景级 Header 列表
     */
    sceneHeaders?: RequestParameter[];

    /**
     * 场景级 Cookie 列表
     */
    sceneCookies?: RequestParameter[];

    /**
     * 场景级变量列表
     */
    sceneVariables?: RequestParameter[];

    /**
     * 场景级断言列表
     */
    sceneAssertions?: AssertParameter[];

    /**
     * 关联的环境配置 ID
     * 场景调试时，会自动将该环境的 baseUrl、变量、Header、Cookie 合并到每个 API 步骤中
     */
    environmentId?: number;

    /**
     * 关联的环境配置名称（冗余字段，方便前端展示）
     */
    environmentName?: string;

    constructor() {
        this.sceneHeaders = [];
        this.sceneCookies = [];
        this.sceneVariables = [];
        this.sceneAssertions = [];
    }
}
