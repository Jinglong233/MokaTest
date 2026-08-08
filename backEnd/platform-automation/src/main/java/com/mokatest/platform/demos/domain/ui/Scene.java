package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName scene
 */
@TableName(value = "scene")
@Data
public class Scene implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 场景id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 所属项目id
     */
    private String projectId;

    /**
     * 父id
     */
    private Integer parentId;

    /**
     * 场景名称
     */
    private String name;

    /**
     * 场景描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 场景类型
     */
    private Object sceneType;

    /**
     * 场景配置
     */
    private String sceneSetting;

    /**
     * 场景分类：UI/API/MIXED
     */
    private String sceneCategory;

    /**
     * 创建时间
     */
    private Date createAt;

    /**
     * 创建人id
     */
    private String createUserId;

    /**
     * 更新人id
     */
    private String updateUserId;

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