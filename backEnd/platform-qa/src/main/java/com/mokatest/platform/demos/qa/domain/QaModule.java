package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 所属模块实体
 */
@TableName(value = "qa_module")
@Data
public class QaModule {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer projectId;

    private Integer parentId;

    private String moduleName;

    private Integer sort;

    private Date createTime;

    private Date updateTime;

    /**
     * 该模块下直接归属的用例数量（非数据库字段，业务统计）
     */
    @TableField(exist = false)
    private Long caseCount;

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
