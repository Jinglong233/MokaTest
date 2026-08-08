package com.mokatest.platform.demos.domain.ui.vo;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.List;

/**
 * @Author JingLong
 * @Description 场景视图对象
 * @Date 2025/8/1 19:58
 **/
@Data
public class SceneVO {

    /**
     * 场景id
     */
    @TableId
    private Integer id;

    /**
     * 所属项目id
     */
    private String projectId;

    /**
     * 父id
     */
    private Integer parentId;

    /**
     * 场景名称
     */
    private String name;

    /**
     * 场景描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 场景类型
     */
    private Object sceneType;

    /**
     * 场景配置
     */
    private String sceneSetting;

    /**
     * 场景分类：UI/API/MIXED
     */
    private String sceneCategory;

    private List<SceneVO> children;
}
