import {AssertParameter} from "@/types/domain/api/requestModel/AssertParameter";
import {GlobalRequestVarType} from "@/types/domain/api/apiEnum/GlobalRequestVarType";

export class GlobalVar {
    /**
     * id
     */
    id?: number;

    /**
     * 所属团队
     */
    teamId?: number;

    /**
     * 参数分类
     */
    type?: GlobalRequestVarType;

    /**
     * 描述
     */
    description?: string;

    /**
     * 参数名称
     */
    name?: string;

    /**
     * 参数值
     */
    value?: string;

    /**
     * 断言数据(当type是assert的时候用)
     */
    globalAssert?: AssertParameter[];

    disabled?: boolean;


    constructor() {
        this.id = undefined;
        this.teamId = undefined;
        this.type = undefined;
        this.description = '';
        this.name = '';
        this.value = '';
        this.globalAssert = undefined;
        this.disabled = false;
    }
}