package com.mokatest.platform.demos.domain.ui.dto.record;

import lombok.Data;

/**
 * 单条草稿步骤（import 返回 / save 入参）
 */
@Data
public class RecordStepDraftVO {

    /**
     * 步骤类型
     */
    private String stepType;

    /**
     * 步骤名称
     */
    private String stepName;

    /**
     * 步骤详情 JSON 对象
     */
    private Object stepDetail;

    /**
     * 是否为密码输入（仅用于前端预览打码）
     */
    private Boolean isPassword;

}
