package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用例与自动化绑定关系实体
 * @TableName test_case_auto_bind
 */
@TableName(value = "test_case_auto_bind")
@Data
public class TestCaseAutoBind {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer testCaseId;

    private String autoType;

    private Integer autoId;

    private String autoName;

    private String bindRemark;

    private Date createTime;
}
