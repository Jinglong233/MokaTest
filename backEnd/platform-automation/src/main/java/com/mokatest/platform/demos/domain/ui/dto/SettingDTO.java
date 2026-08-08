package com.mokatest.platform.demos.domain.ui.dto;// 步骤设置

import com.mokatest.platform.demos.domain.ui.Setting;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import lombok.Data;

import java.util.List;


@Data

public class SettingDTO {
    private Setting setting;
    private List<AssertStepDTO> assertList;
    private List<ExtractStepDTO> extractList;
}