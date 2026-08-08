package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 测试集与用例关联关系实体
 */
@TableName(value = "test_case_set_relation")
@Data
public class TestCaseSetRelation {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer setId;

    private Integer testCaseId;

    private Date createTime;
}
