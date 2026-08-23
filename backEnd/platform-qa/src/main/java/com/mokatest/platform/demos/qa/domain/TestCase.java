package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 文字用例实体
 * @TableName test_case
 */
@TableName(value = "test_case", autoResultMap = true)
@Data
public class TestCase {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String caseCode;

    private String caseName;

    private String preCondition;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<TestStepItem> testSteps;

    private String caseType;

    private String priority;

    private String status;

    private Integer projectId;

    private Integer moduleId;

    private String lastResult;

    private Date lastExecuteTime;

    private String tags;

    private Integer expectDuration;

    /** 同级（同模块内）排序值，脑图拖拽/新建顺序持久化用 */
    private Integer sort;

    private Integer requirementId;

    /** 关联需求ID列表（逗号分隔，支持多需求关联；requirement_id 保持为列表第一个，兼容旧逻辑） */
    private String requirementIds;

    @TableField(exist = false)
    private List<Integer> setIds;

    /** 创建人名称（非表字段，详情/列表关联查询填充） */
    @TableField(exist = false)
    private String createUserName;

    /** 更新人名称（非表字段，详情/列表关联查询填充） */
    @TableField(exist = false)
    private String updateUserName;

    /** 所属模块名称（非表字段，详情/列表关联查询填充） */
    @TableField(exist = false)
    private String moduleName;

    private Integer createUserId;

    private Integer updateUserId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    private Date deletedAt;

    /**
     * 测试步骤项（用于JSON序列化）
     */
    @Data
    public static class TestStepItem {
        private String step;
        private String expected;
    }
}
