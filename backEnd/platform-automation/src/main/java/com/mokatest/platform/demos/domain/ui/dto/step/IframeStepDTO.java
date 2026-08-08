package com.mokatest.platform.demos.domain.ui.dto.step;// iframe操作


import com.mokatest.platform.demos.domain.ui.uiEnum.iframe.SwitchIframeType;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

@Data
public class IframeStepDTO extends StepBaseDTO {
    private String stepType;
    // 切换方式
    private SwitchIframeType switchIframeType;
    // 目标元素
    private ElementDTO element;
    private String url;
    private String iframeName;
    private Integer iframeIndex;
    private String iframeId;
}






