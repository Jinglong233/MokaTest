/**
 * Webhook 通知配置
 * 对应后端 PlanWebhook 实体
 */
export class PlanWebhook {
    id?: number;
    projectId?: number;
    /** 配置名称，如 "钉钉-测试群" */
    name?: string = '';
    /** 是否启用 */
    enabled?: boolean = true;
    /** 平台类型：DINGTALK / WECHAT / FEISHU / CUSTOM */
    type?: string = 'DINGTALK';
    /** Webhook URL */
    url?: string = '';
    /** 签名密钥 */
    secret?: string = '';
    /** 触发时机，逗号分隔：SUCCESS,FAILURE */
    notifyOn?: string = 'SUCCESS,FAILURE';
    /** @手机号，逗号分隔 */
    atMobiles?: string = '';
    createTime?: string;
    updateTime?: string;
    isDeleted?: number = 0; // 是否已删除：0-未删除，1-已删除
    deletedAt?: Date; // 删除时间
}
