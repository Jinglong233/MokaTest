import {AuthType} from "@/types/domain/api/apiEnum/AuthType";

/**
 * 接口鉴权配置（HTTP 接口调试时自动附加认证信息）
 *
 * token / username / password / keyValue 均支持 ${var} 变量引用，
 * 执行器在变量替换后拼接到 Authorization 头或自定义 Header/Query。
 * 优先级低于接口 Header 表：手写了同名头时以手写为准。
 */
export class AuthConfig {
    /** 鉴权类型（默认 NONE） */
    authType: AuthType = AuthType.NONE;
    /** BEARER：token 值（支持 ${var}） */
    token?: string;
    /** BASIC：用户名（支持 ${var}） */
    username?: string;
    /** BASIC：密码（支持 ${var}） */
    password?: string;
    /** API_KEY：参数名（Header 名或 Query 参数名） */
    keyName?: string;
    /** API_KEY：参数值（支持 ${var}） */
    keyValue?: string;
    /** API_KEY：附加位置，header / query（默认 header） */
    keyIn: string = 'header';
}
