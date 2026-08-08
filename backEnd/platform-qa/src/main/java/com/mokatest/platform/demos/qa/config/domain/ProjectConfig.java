package com.mokatest.platform.demos.qa.config.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 项目级统一配置（差量存储）。
 * 无记录 = 平台默认行为，新项目零初始化。
 *
 * config_type：NOTIFY_RULE（通知规则）/ FIELD_VISIBLE（字段显隐）
 * config_key：NOTIFY_RULE 时为事件类型（如 BUG_STATUS_CHANGED）；FIELD_VISIBLE 时为业务对象（bug / requirement / test_case）
 */
@TableName(value = "project_config")
@Data
public class ProjectConfig {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer projectId;

    private String configType;

    private String configKey;

    /**
     * 配置值（JSON 字符串）
     */
    private String configValue;

    private Integer createUserId;

    private Integer updateUserId;

    private Date createTime;

    private Date updateTime;
}
