package com.mokatest.platform.demos.domain.ui.dto.step;

import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

import java.util.List;

@Data
public class UploadFileStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private ElementDTO element; // 文件输入框元素
    private List<String> fileIds; // 上传文件的fileId列表
}
