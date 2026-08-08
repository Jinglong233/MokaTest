package com.mokatest.platform.demos.qa.domain.vo;

import com.mokatest.platform.demos.qa.domain.TestCase;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 文字用例列表视图对象
 */
@Data
public class TestCaseVO {

    private Integer id;

    private String caseCode;

    private String caseName;

    private String preCondition;

    private List<TestCase.TestStepItem> testSteps;

    private String caseType;

    private String priority;

    private String status;

    private Integer projectId;

    private Integer requirementId;

    /** 关联需求标题（后端关联查询） */
    private String requirementTitle;

    /** 关联BUG数量（后端统计） */
    private Long bugCount;

    private Integer createUserId;

    /** 创建人名称（后端关联查询） */
    private String createUserName;

    private Integer moduleId;

    /** 模块名称（后端关联查询） */
    private String moduleName;

    /** 所属测试集ID列表 */
    private List<Integer> setIds;

    /** 所属测试集名称（逗号分隔，后端关联查询） */
    private String setNames;

    private String lastResult;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastExecuteTime;

    private String tags;

    private Integer expectDuration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
