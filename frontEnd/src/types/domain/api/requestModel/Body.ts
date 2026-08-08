import {BodyMode} from "@/types/domain/api/apiEnum/BodyMode";
import {RequestParameter} from "@/types/domain/api/requestModel/RequestParameter";
import {ResponseSchema} from "@/types/domain/api/requestModel/ResponseSchema";

export class Body {
    mode?: BodyMode;
    formData?: RequestParameter[];
    xWwwFormUrlencoded?: RequestParameter[];
    json?: string;
    xml?: string;
    /** 请求体结构绑定（mode=JSON 时生效）：配置后执行时按定义生成请求体，优先于手写 json */
    schemaBinding?: ResponseSchema;

    constructor() {
        this.mode = BodyMode.NONE;
        this.formData = [new RequestParameter()];
        this.xWwwFormUrlencoded = [new RequestParameter()];
        this.json = '';
        this.xml = '';
    }
}
