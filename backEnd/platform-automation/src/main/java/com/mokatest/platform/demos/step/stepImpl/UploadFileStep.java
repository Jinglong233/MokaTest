package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.api.service.FileUploadService;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractNecessaryElementTestStep;
import com.microsoft.playwright.Locator;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传步骤
 */
public class UploadFileStep extends AbstractNecessaryElementTestStep {

    private List<String> fileIds;

    public UploadFileStep(ElementDTO element, List<String> fileIds) {
        super(element);
        this.fileIds = fileIds;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        if (fileIds == null || fileIds.isEmpty()) {
            throw new RuntimeException("未配置上传文件");
        }

        FileUploadService fileUploadService = ApplicationContextHolder.getBean(FileUploadService.class);
        List<Path> filePaths = new ArrayList<>();
        for (String fileId : fileIds) {
            File file = fileUploadService.getFile(fileId);
            if (file == null || !file.exists()) {
                throw new RuntimeException("上传文件不存在：fileId=" + fileId);
            }
            filePaths.add(file.toPath());
        }

        Locator locator = getLocator(context);

        // 兼容单文件 input：若目标元素没有 multiple 属性，只取第一个文件
        Object isMultipleObj = locator.evaluate("el => el.multiple");
        boolean isMultiple = Boolean.TRUE.equals(isMultipleObj);
        List<Path> finalFilePaths = filePaths;
        if (!isMultiple && filePaths.size() > 1) {
            finalFilePaths = filePaths.subList(0, 1);
        }

        locator.setInputFiles(finalFilePaths.toArray(new Path[0]));

        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }
}
