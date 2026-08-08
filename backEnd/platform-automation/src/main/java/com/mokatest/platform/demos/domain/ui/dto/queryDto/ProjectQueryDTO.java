package com.mokatest.platform.demos.domain.ui.dto.queryDto;

import java.util.Date;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/11/13 14:25
 **/
public class ProjectQueryDTO extends BasePageQueryDTO {
    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 描述
     */
    private String description;

    /**
     * 更新人ID
     */
    private String updateUserId;

    /**
     * 覆盖率
     */
    private Integer coverage;

    /**
     * 状态
     */
    private Object status;

    /**
     * api测试用例数量
     */
    private Integer apiTotal;

    /**
     * UI测试场景数量
     */
    private Integer uiTotal;

    /**
     * 性能测试用例数量
     */
    private Integer performanceTotal;

    /**
     * 计划数量
     */
    private Integer planTotal;

    /**
     * UI测试报告通过率
     */
    private Integer uiPass;

    /**
     * 标签
     */
    private Object tagClassify;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人ID
     */
    private String createUserId;

    /**
     * 创建人姓名
     */
    private String createUserName;

}
