package com.mokatest.platform.demos.api.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mokatest.platform.demos.api.domain.apiEnum.DataTemplateNodeType;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import lombok.Data;

import java.util.Date;

/**
 * 数据模板表
 *
 * @author JingLong
 * @since 2026-06-17
 */
@TableName(value = "data_template", autoResultMap = true)
@Data
public class DataTemplate {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 所属团队id
     */
    private Integer teamId;

    /**
     * 所属项目id
     */
    private Integer projectId;

    /**
     * 父节点ID，0=根节点
     */
    private Integer parentId;

    /**
     * 节点类型：FOLDER / TEMPLATE
     */
    private DataTemplateNodeType nodeType;

    /**
     * 模板名称/文件夹名称
     */
    private String templateName;

    /**
     * 描述
     */
    private String description;

    /**
     * 模板字段规则根节点（fieldType = OBJECT）。仅 TEMPLATE 节点有效。
     * 旧数据为 List<TemplateFieldRule> 数组，通过 {@link MockFieldRule} 类级反序列化器兼容。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private MockFieldRule templateSchema;

    /**
     * 继承的父模板ID（仅 TEMPLATE 节点有效）。
     * 生成/校验时先加载父模板 schema 作为基础，子模板同名字段覆盖、新字段追加。
     */
    private Integer extendsId;

    /**
     * 是否共享：1-共享，0-私有。仅 TEMPLATE 节点有效。
     */
    private Integer isShared;

    /**
     * 同级排序
     */
    private Integer sort;

    /**
     * 创建者id
     */
    private Integer createUserId;

    /**
     * 更新者id
     */
    private Integer updateUserId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private Date deletedAt;
}
