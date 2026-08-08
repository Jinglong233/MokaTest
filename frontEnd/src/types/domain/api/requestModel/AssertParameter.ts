import {ApiAssertType} from "@/types/domain/api/apiEnum/ApiAssertType";
import {AssertRelationship} from "@/types/enum/condation/AssertRelationship";

export class AssertParameter {
    // 断言体
    apiAssertType?: ApiAssertType;
    // 字段
    field?: string;
    // 断言关系
    assertRelationship?: AssertRelationship;
    // 断言值
    assertValue?: string;

    // 是否禁用
    disabled?: boolean;

    /**
     * 断言规则来源（运行时填充，不持久化到数据库）
     * GLOBAL | ENVIRONMENT | SCENE | API
     */
    source?: string;

    constructor() {
        this.apiAssertType = ApiAssertType.BODY,
            this.field = '',
            this.assertRelationship = AssertRelationship.EQUALS,
            this.assertValue = '',
            this.disabled = false
    }
}
