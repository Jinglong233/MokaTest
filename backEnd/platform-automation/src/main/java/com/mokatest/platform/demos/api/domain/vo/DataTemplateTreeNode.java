package com.mokatest.platform.demos.api.domain.vo;

import com.mokatest.platform.demos.api.domain.apiEnum.DataTemplateNodeType;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 数据模板树节点（文件夹 + 模板混合树）
 *
 * @author JingLong
 * @since 2026-06-22
 */
@Data
public class DataTemplateTreeNode {

    private Integer id;
    private String name;
    private Integer parentId;
    private DataTemplateNodeType nodeType;
    private String description;
    private Integer isShared;
    private Date updateTime;
    private Integer sort;
    private Integer templateCount;
    private List<DataTemplateTreeNode> children;
}
