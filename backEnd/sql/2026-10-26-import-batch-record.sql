-- 2026-10-26 导入幂等批次记录表
-- 场景 JSON 导入（/scene/importScenesJson）、CRX 导入（/scene/importScene）与录制保存（/record/save）共用的幂等记录：
-- 客户端生成 batchId，重复提交时按 batch_id + entry_type 查重并返回首次导入结果

create table if not exists import_batch_record
(
    id         int auto_increment comment '记录ID'
        primary key,
    batch_id   varchar(64)                          not null comment '幂等批次ID（客户端生成）',
    project_id varchar(36)                          not null comment '所属项目ID',
    entry_type varchar(32)                          not null comment '入口类型：SCENE_IMPORT=场景JSON导入，CRX_IMPORT=CRX录制数据导入，RECORD_SAVE=录制保存',
    result     varchar(255)                         null comment '首次导入结果（场景ID或true）',
    created_at datetime default CURRENT_TIMESTAMP   null comment '创建时间',
    constraint uk_import_batch_id unique (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;
