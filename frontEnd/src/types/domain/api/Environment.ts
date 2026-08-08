import {RequestParameter} from "@/types/domain/api/requestModel/RequestParameter";
import {ServeParameter} from "@/types/domain/api/requestModel/ServeParameter";
import {DataBaseParameter} from "@/types/domain/api/requestModel/DataBaseParameter";

export class Environment {
    /**
     * 环境id
     */
    id?: number;

    /**
     * 环境名称
     */
    envName?: string;

    /**
     * 所属团队id
     */
    teamId?: number;

    /**
     * cookie列表
     */
    cookies?: RequestParameter[];

    /**
     * header列表
     */
    headers?: RequestParameter[];


    /**
     * 环境变量列表
     */

    envVar?: RequestParameter[];

    /**
     * 服务
     */
    serve?: ServeParameter[];

    /**
     * 数据库列表
     */
    dbs?: DataBaseParameter[];

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
     * 更新人id
     */
    updateUserId?: number;
}