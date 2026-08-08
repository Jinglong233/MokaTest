package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.dialog.DialogOperationType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.microsoft.playwright.Page;

/**
 * @Author JingLong
 * @Description 对话框操作步骤
 * @Date 2025/12/30 11:35
 **/
public class DialogStep extends AbstractTestStep {
    private DialogOperationType dialogOperationType;

    private String dialogMessage;

    public DialogStep(DialogOperationType dialogOperationType) {
        this.dialogOperationType = dialogOperationType;
    }


    public DialogStep(DialogOperationType dialogOperationType, String dialogMessage) {
        this.dialogOperationType = dialogOperationType;
        this.dialogMessage = dialogMessage;
    }

    @Override
    protected StepResult doExecute(TestExecutionContext context) {
        Page currentPage = context.getCurrentPage();
        // 判断操作类型
        switch (dialogOperationType) {
            case ACCEPT -> {
                currentPage.onceDialog(dialog -> {
                    context.getCurrentPage().waitForTimeout(1000);
                    dialog.accept();
                });
            }
            case DISMISS -> {
                currentPage.onceDialog(dialog -> {
                    context.getCurrentPage().waitForTimeout(1000);
                    dialog.dismiss();
                });
            }
            case MESSAGE -> {
                currentPage.onceDialog(dialog -> {
                    // 获取变量名称
                    String message = dialog.message();
                    if (dialogMessage == null || "".equals(dialogMessage.trim())) {
                        throw new RuntimeException("未找到接收变量");
                    }
                    context.getVariables().put(dialogMessage, message);
                });

            }
            default -> throw new RuntimeException("不支持的对话框操作类型:" + dialogOperationType.name());
        }
        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);

        return context.getCurrentStepResult();
    }
}
