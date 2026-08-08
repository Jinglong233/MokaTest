/**
 * API 数据提取配置模型
 *
 * 功能说明：定义从 HTTP 响应中提取数据的规则，用于将响应中的关键数据提取到变量中，
 * 供后续接口请求使用（如登录后提取 token，下一个接口请求头中使用 {{token}}）
 *
 * 使用流程：
 *   1. 在接口配置中设置提取规则（type + expression + variableName）
 *   2. 接口执行完成后，提取引擎根据规则从响应中提取数据
 *   3. 提取结果存入变量池，key 为 variableName，value 为提取到的值
 *   4. 后续接口通过 {{variableName}} 或 ${variableName} 引用该变量
 *
 * 配置示例：
 *   {
 *     type: ExtractType.JSON_PATH,
 *     expression: "$.data.token",
 *     variableName: "token",
 *     defaultValue: "",
 *     description: "提取登录token"
 *   }
 */
import {ExtractType} from "@/types/domain/api/apiEnum/ExtractType";

export class ApiExtraction {
    /** 提取类型，决定从响应的哪个部分提取数据 */
    type?: ExtractType;

    /**
     * 提取表达式，根据 type 的不同含义不同
     *   JSON_PATH  : JSONPath 表达式，如 "$.data.token"
     *   REGEX      : 正则表达式，如 "token":"([^"]+)"
     *   HEADER     : 响应头名称，如 "Authorization"
     *   COOKIE     : Cookie 名称，如 "sessionId"
     *   STATUS_CODE: 无需填写（可为空）
     */
    expression?: string;

    /** 变量名，提取成功后存入变量池的 key，后续接口通过 {{variableName}} 引用 */
    variableName?: string;

    /** 默认值，当提取失败时使用的备用值 */
    defaultValue?: string;

    /** 提取规则描述，用于前端展示 */
    description?: string;

    /**
     * 是否禁用该提取规则
     * true 表示不生效，false 表示生效（默认）
     */
    disabled?: boolean;

    /**
     * 提取规则来源（运行时填充，不持久化到数据库）
     * GLOBAL | ENVIRONMENT | SCENE | API
     */
    source?: string;

    constructor() {
        this.type = ExtractType.JSON_PATH;
        this.expression = '';
        this.variableName = '';
        this.defaultValue = '';
        this.description = '';
        this.disabled = false;
    }
}
