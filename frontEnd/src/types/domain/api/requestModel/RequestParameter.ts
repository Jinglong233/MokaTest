import {ParameterType} from "@/types/domain/api/apiEnum/ParameterType";
import {MockConfig} from "@/types/domain/api/requestModel/MockConfig";

export class RequestParameter {
    name?: string;
    value?: string;
    type?: ParameterType;
    description?: string;
    disabled?: boolean;
    /** 参数级 Mock 结构化配置 */
    mockConfig?: MockConfig;

    constructor() {
        this.type = ParameterType.STRING;
        this.disabled = false;
        this.description = '';
        this.name = '';
    }
}
