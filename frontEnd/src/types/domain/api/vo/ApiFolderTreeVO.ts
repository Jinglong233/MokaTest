import {ApiNodeType} from "../apiEnum/ApiNodeType";
import {RequestMethod} from "../apiEnum/RequestMethod";
import {ApiType} from "../apiEnum/ApiType";

export class ApiFolderTreeVO {
    /**
     * id
     */
    id?: number;

    /**
     * 父id
     */
    parentId?: number;

    /**
     * 所属项目id
     */
    projectId?: number;

    /**
     * 所属团队id
     */
    teamId?: number;

    /**
     * 接口名称
     */
    apiName?: string;

    /**
     * 节点类型
     */
    apiNode?: ApiNodeType;

    /**
     * 请求方法（仅接口节点）
     */
    requestMethod?: RequestMethod;

    /**
     * 接口类型（HTTP / SQL / TCP / WEBSOCKET）
     */
    apiType?: ApiType;

    /**
     * 排序
     */
    sort?: number;


    /**
     * 创建时间
     */
    createTime?: Date;

    /**
     * 创建者id
     */
    createUserId?: number;

    /**
     * 更新时间
     */
    updateTime?: Date;

    /**
     * 更新者id
     */
    updateUserId?: number;

    children?: ApiFolderTreeVO[];
}
