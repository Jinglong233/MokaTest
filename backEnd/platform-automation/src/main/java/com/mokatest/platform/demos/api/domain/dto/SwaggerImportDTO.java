package com.mokatest.platform.demos.api.domain.dto;

import lombok.Data;

/**
 * Swagger / OpenAPI 导入请求参数
 *
 * @author JingLong
 * @since 2026-07-03
 */
@Data
public class SwaggerImportDTO {

    /**
     * 所属项目 ID
     */
    private Integer projectId;

    /**
     * 所属团队 ID
     */
    private Integer teamId;

    /**
     * 目标父目录 ID，默认根目录
     */
    private Integer parentId = 0;

    /**
     * 是否按 tag 自动创建文件夹
     */
    private Boolean groupByTags = true;

    /**
     * 是否覆盖已存在接口
     */
    private Boolean overwrite = false;

    /**
     * Swagger 文档 URL（与 file 二选一）
     */
    private String url;
}
