package com.mokatest.platform.demos.domain.ui.dto.other;

import com.mokatest.platform.demos.domain.ui.TestStep;
import lombok.Data;

/**
 * @Author JingLong
 * @Description 添加相邻步骤DTO
 * @Date 2026/1/1 12:35
 **/
@Data
public class AddAdjacentStepDTO {
    private String targetStepId;
    private TestStep addStep;

    /**
     * 是否是子步骤
     */
    private Boolean isChildren;
}
