package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Webhook 通知配置实体
 * 
 * 对应数据库表 {@code plan_webhook}，用于存储计划执行完成后的第三方通知配置。
 * 一个项目可以配置多个 Webhook，执行时若计划开启了通知，则会向该项目下所有
 * 启用的 Webhook 配置逐一发送通知。
 *
 * @see com.mokatest.platform.demos.domain.ui.Plan#getWebhookEnabled()
 * @see com.mokatest.platform.demos.service.WebhookNotifyService
 */
@Data
@TableName(value = "plan_webhook")
public class PlanWebhook implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 所属项目ID
     * 按项目隔离配置，查询时通过 project_id 过滤
     */
    private Integer projectId;

    /**
     * 配置名称
     * 用于前端展示，如 "钉钉-测试群"、"企微-告警群"
     */
    private String name;

    /**
     * 是否启用
     * true = 该配置参与发送；false = 跳过该配置
     */
    private Boolean enabled;

    /**
     * 平台类型
     * 取值见 {@link com.mokatest.platform.demos.domain.ui.uiEnum.WebhookType}
     */
    private String type;

    /**
     * Webhook 请求地址
     * 钉钉/企微/飞书为平台提供的完整 URL；自定义类型为用户自行提供的 HTTP 地址
     */
    private String url;

    /**
     * 签名密钥
     * 钉钉/企微/飞书开启 "加签" 安全设置时填写，用于 HmacSHA256 签名计算
     * 允许更新为 null（清空时使用），故设置 updateStrategy = IGNORED
     */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.IGNORED)
    private String secret;

    /**
     * 触发时机
     * 逗号分隔字符串，如 "SUCCESS,FAILURE"。
     * 执行完成后根据报告结果匹配：步骤失败数 > 0 视为 FAILURE，否则为 SUCCESS
     */
    private String notifyOn;

    /**
     * @指定人手机号
     * 逗号分隔字符串，如 "13800138000,13900139000"。
     * 仅钉钉/企微有效，会在消息中 @对应成员
     */
    private String atMobiles;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建人ID
     */
    private Integer createUserId;

    /**
     * 更新人ID
     */
    private Integer updateUserId;

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
