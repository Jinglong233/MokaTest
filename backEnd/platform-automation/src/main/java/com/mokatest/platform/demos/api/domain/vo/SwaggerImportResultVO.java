package com.mokatest.platform.demos.api.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Swagger / OpenAPI 导入结果
 *
 * @author JingLong
 * @since 2026-07-03
 */
@Data
public class SwaggerImportResultVO {

    /**
     * 新建/复用文件夹数量
     */
    private int folderCount;

    /**
     * 成功导入接口数量
     */
    private int interfaceCount;

    /**
     * 跳过接口数量
     */
    private int skippedCount;

    /**
     * 覆盖接口数量
     */
    private int overwrittenCount;

    /**
     * 跳过记录明细
     */
    private List<SkippedOperationVO> skipped = new ArrayList<>();

    /**
     * 添加跳过记录
     */
    public void addSkipped(String path, String method, String reason) {
        skipped.add(new SkippedOperationVO(path, method, reason));
        skippedCount++;
    }

    /**
     * 添加覆盖记录
     */
    public void addOverwritten() {
        overwrittenCount++;
    }
}
