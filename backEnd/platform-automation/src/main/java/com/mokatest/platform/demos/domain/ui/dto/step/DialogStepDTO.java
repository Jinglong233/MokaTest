package com.mokatest.platform.demos.domain.ui.dto.step;

import com.mokatest.platform.demos.domain.ui.uiEnum.dialog.DialogOperationType;
import lombok.Data;

/**
 * @Author JingLong
 * @Description 对话框传输对象
 * @Date 2025/12/30 11:32
 **/
@Data
public class DialogStepDTO extends StepBaseDTO {
    private String stepType;
    // 对话框操作方式
    private DialogOperationType dialogOperationType;

    // 对话框消息
    private String dialogMessage;

}