package com.mokatest.platform.demos.qa.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 需求池列表视图对象
 */
@Data
public class RequirementVO {

    private Integer id;

    private String reqCode;

    private String title;

    private String description;

    private String priority;

    private String status;

    private Integer projectId;

    private String version;

    private Integer ownerId;

    /** 负责人名称（后端关联查询） */
    private String ownerName;

    /** 关联用例数 */
    private Long caseCount;

    /** 关联BUG总数 */
    private Long bugCount;

    /** 未关闭BUG数 */
    private Long openBugCount;

    private Integer moduleId;

    /** 模块名称（后端关联查询） */
    private String moduleName;

    private Integer parentId;

    private String reqType;

    private String source;

    private String participants;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expectReleaseTime;

    private String tags;

    private Integer createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
