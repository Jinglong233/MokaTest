package com.mokatest.platform.demos.api.domain.vo;

import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * @Description:
 * @Author: JingLong
 * @DateTime: 2026/4/7 16:48
 */
@Data
public class ApiFolderTreeVO {
    /**
     * id
     */
    private Integer id;

    /**
     * 父id
     */
    private Integer parentId;

    /**
     * 所属项目id
     */
    private Integer projectId;

    /**
     * 所属团队id
     */
    private Integer teamId;

    /**
     * 接口名称
     */
    private String apiName;

    /**
     * 节点类型
     */
    private ApiNodeType apiNode;

    /**
     * 请求方法（仅接口节点）
     */
    private String requestMethod;

    /**
     * 接口类型（HTTP / SQL / TCP / WEBSOCKET）
     */
    private String apiType;

    /**
     * 排序
     */
    private Integer sort;


    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建者id
     */
    private Integer createUserId;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 更新者id
     */
    private Integer updateUserId;

    private List<ApiFolderTreeVO> children;
}
