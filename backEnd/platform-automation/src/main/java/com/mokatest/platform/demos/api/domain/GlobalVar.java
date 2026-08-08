package com.mokatest.platform.demos.api.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mokatest.platform.demos.api.domain.apiEnum.GlobalRequestVarType;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 全局参数表
 * @TableName global_var
 */
@TableName(value = "global_var", autoResultMap = true)
@Data
public class GlobalVar {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 所属团队
     */
    private Integer teamId;

    /**
     * 参数分类
     */
    private GlobalRequestVarType type;

    /**
     * 描述
     */
    private String description;

    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数值
     */
    private String value;

    /**
     * 断言数据(当type是assert的时候用)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AssertParameter> globalAssert;

    /**
     * 是否禁用
     */
    private boolean disabled;

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