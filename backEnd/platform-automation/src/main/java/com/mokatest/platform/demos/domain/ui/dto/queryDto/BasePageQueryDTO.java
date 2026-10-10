package com.mokatest.platform.demos.domain.ui.dto.queryDto;

import lombok.Data;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/11/5 14:12
 **/
@Data
public class BasePageQueryDTO {
    private Integer pageNum;
    private Integer pageSize;

    /** pageSize 统一上限 200，防止超大分页拖垮查询（默认 10） */
    public Integer getPageSize() {
        if (pageSize == null || pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, 200);
    }
}
