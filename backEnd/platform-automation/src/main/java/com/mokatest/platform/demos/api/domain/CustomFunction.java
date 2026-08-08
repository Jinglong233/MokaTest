package com.mokatest.platform.demos.api.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 自定义公共函数表
 *
 * 用户用 JS 编写项目级公共函数，在参数值/Mock/脚本中通过
 * {@code {{__CUSTOM(id, args)__}}} / {@code @custom(id, args)} / {@code context.utils.custom(id, ...)} 调用。
 *
 * @author JingLong
 * @since 2026-07-31
 */
@TableName("custom_function")
@Data
public class CustomFunction {

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
     * 函数名称（展示用）
     */
    private String funcName;

    /**
     * 参数名定义（逗号分隔，如 text,key）
     */
    private String funcParams;

    /**
     * JS 函数体（return 出结果）
     */
    private String funcCode;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序
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
