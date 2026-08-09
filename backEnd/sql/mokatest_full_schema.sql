-- ============================================================
-- MokaTest 全量建库脚本
-- 注意：请在 utf8mb4 数据库中执行本脚本，建库语句：
--   CREATE DATABASE mokatest DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
-- ============================================================
SET NAMES utf8mb4;

create table api_request
(
    id                     int auto_increment comment 'id'
        primary key,
    parent_id              int                                               default 0                 not null comment '父id',
    project_id             int                                                                         not null comment '所属项目id',
    team_id                int                                                                         not null comment '所属团队id',
    api_name               varchar(100)                                                                not null comment '名称',
    api_node               enum ('INTERFACE', 'FOLDER')                                                not null comment '接口分类',
    request_method         enum ('POST', 'PUT', 'GET', 'DELETE')                                       null comment '请求方式',
    request_path           varchar(500)                                                                null comment '请求路径',
    request_header         json                                                                        null comment '请求头',
    cookies                json                                                                        null comment 'cookie',
    query                  json                                                                        null comment 'query参数',
    body                   json                                                                        null comment 'body请求体',
    env_info               json                                                                        null comment '关联的环境数据',
    api_type               enum ('HTTP', 'TCP', 'SQL', 'WEBSOCKET', 'DUBBO') default 'HTTP'            null comment '接口类型',
    sql_config             json                                                                        null comment 'SQL调试配置 {sql, dbConnectionName, dbConfig, timeout, maxRows, params}',
    sort                   int                                               default 0                 null comment '排序',
    association_extraction json                                                                        null comment '关联提取',
    pre_script             text                                                                        null comment '前置脚本',
    post_script            text                                                                        null comment '后置脚本',
    mock_response          json                                                                        null comment 'Mock响应配置',
    auth_config            json                                                                        null comment '接口鉴权配置（AuthConfig JSON）',
    response_examples      json                                                                        null comment '响应示例列表',
    response_schema        json                                                                        null comment '响应定义 {mode, templateId, schema, overrides, validateEnabled}',
    api_result_assert      json                                                                        null comment '断言',
    source_drat_id         int                                                                         null comment '来源id',
    create_time            datetime                                          default CURRENT_TIMESTAMP null comment '创建时间',
    create_user_id         int                                                                         not null comment '创建者id',
    update_time            datetime                                          default CURRENT_TIMESTAMP null comment '更新时间',
    update_user_id         int                                                                         null comment '更新者id',
    is_deleted             tinyint                                           default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at             datetime                                                                    null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_api_request_is_deleted
    on api_request (is_deleted);

create index idx_api_request_project_id
    on api_request (project_id);

create table bug
(
    id              int auto_increment
        primary key,
    bug_code        varchar(32)                                                                                              not null comment 'BUG编号 BUG-xxx',
    title           varchar(255)                                                                                             not null comment '标题',
    description     text                                                                                                     null comment 'BUG描述',
    reproduce_steps text                                                                                                     null comment '复现步骤',
    severity        enum ('FATAL', 'SERIOUS', 'NORMAL', 'TIPS')                                    default 'NORMAL'          null comment '严重程度',
    priority        enum ('URGENT', 'HIGH', 'MEDIUM', 'LOW')                                       default 'MEDIUM'          null,
    status          enum ('NEW', 'CONFIRMED', 'FIXING', 'FIXED', 'VERIFIED', 'CLOSED', 'REJECTED') default 'NEW'             null,
    project_id      int                                                                                                      not null,
    module_id       int                                                                                                      null comment '所属模块',
    deadline        datetime                                                                                                 null comment '截止日期',
    environment     varchar(20)                                                                    default 'TEST'            null comment '环境: TEST/STAGING/PROD',
    found_version   varchar(50)                                                                                              null comment '发现版本',
    fixed_version   varchar(50)                                                                                              null comment '修复版本',
    reproduce_rate  varchar(20)                                                                    default 'ALWAYS'          null comment '重现概率: ALWAYS/OFTEN/SOMETIMES/RARE',
    close_reason    varchar(30)                                                                                              null comment '关闭原因: FIXED/DUPLICATE/NOT_BUG/CANNOT_REPRODUCE/WONT_FIX',
    tags            varchar(255)                                                                                             null comment '标签',
    requirement_id  int                                                                                                      null comment '关联需求',
    test_case_id    int                                                                                                      null comment '关联用例',
    plan_case_id    int                                                                                                      null comment '关联的测试计划用例ID',
    reporter_id     int                                                                                                      not null comment '鎶ュ憡浜',
    assignee_id     int                                                                                                      null comment '鎸囨淳缁',
    create_user_id  int                                                                                                      not null,
    update_user_id  int                                                                                                      null,
    create_time     datetime                                                                       default CURRENT_TIMESTAMP null,
    update_time     datetime                                                                       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_deleted      tinyint                                                                        default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at      datetime                                                                                                 null comment '删除时间',
    constraint uk_bug_code
        unique (bug_code, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_assignee
    on bug (assignee_id);

create index idx_bug_is_deleted
    on bug (is_deleted);

create index idx_deadline
    on bug (deadline);

create index idx_module
    on bug (module_id);

create index idx_project_status
    on bug (project_id, status);

create table bug_comment
(
    id               int auto_increment
        primary key,
    bug_id           int                                not null comment 'BugID',
    content          text                               not null comment '评论内容',
    mention_user_ids varchar(500)                       null comment '@的用户ID（JSON数组）',
    create_user_id   int                                null comment '评论人',
    create_time      datetime default CURRENT_TIMESTAMP null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_bug
    on bug_comment (bug_id);

create table bug_operation_log
(
    id           int auto_increment
        primary key,
    bug_id       int                                not null comment 'BugID',
    field_name   varchar(50)                        null comment '变更字段',
    old_value    varchar(500)                       null comment '旧值',
    new_value    varchar(500)                       null comment '新值',
    operator_id  int                                null comment '操作人',
    operate_time datetime default CURRENT_TIMESTAMP null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_bug
    on bug_operation_log (bug_id);

create table data_template
(
    id              int auto_increment comment 'id'
        primary key,
    team_id         int                                                   not null comment '所属团队id',
    project_id      int                                                   not null comment '所属项目id',
    parent_id       int                         default 0                 null comment '父节点ID，0=根',
    node_type       enum ('FOLDER', 'TEMPLATE') default 'TEMPLATE'        not null comment '节点类型',
    sort            int                         default 0                 null comment '同级排序',
    folder_id       int                         default 0                 null comment '所属文件夹ID，0=根',
    template_name   varchar(100)                                          not null comment '模板名称',
    description     varchar(500)                                          null comment '描述',
    template_schema json                                                  null,
    extends_id      int                                                   null comment '继承的父模板ID（仅 TEMPLATE 节点）',
    is_shared       tinyint                     default 1                 null comment '是否共享：1-共享，0-私有',
    create_user_id  int                                                   not null comment '创建者id',
    update_user_id  int                                                   null comment '更新者id',
    create_time     datetime                    default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime                    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_deleted      tinyint                     default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at      datetime                                              null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_data_template_folder
    on data_template (folder_id, is_deleted);

create table custom_function
(
    id             int auto_increment comment 'id'
        primary key,
    team_id        int                                  not null comment '所属团队id',
    project_id     int                                  not null comment '所属项目id',
    func_name      varchar(100)                         not null comment '函数名称（展示用）',
    func_params    varchar(500)                         null comment '参数名定义（逗号分隔，如 text,key）',
    func_code      text                                 not null comment 'JS 函数体（return 出结果）',
    description    varchar(500)                         null comment '描述',
    sort           int                      default 0   null comment '排序',
    create_user_id int                                  not null comment '创建者id',
    update_user_id int                                  null comment '更新者id',
    create_time    datetime                 default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    datetime                 default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_deleted     tinyint                  default 0   not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                             null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_custom_function_project
    on custom_function (project_id, is_deleted);

create table element
(
    id             int auto_increment comment '元素ID'
        primary key,
    parent_id      int                                                                                                     not null comment '父id',
    element_name   varchar(255)                                                                                            not null comment '元素名称',
    element_type   enum ('ELEMENT', 'FOLDER')                                                                              not null comment '类型',
    locator_type   enum ('id', 'class', 'xpath', 'css', 'text', 'placeholder', 'role', 'test_id', 'label', 'title', 'alt') null comment '定位类型',
    locator_value  text                                                                                                    null comment '定位值',
    sort           int                                                                                                     null comment '排序',
    description    text                                                                                                    null comment '元素描述',
    project_id     varchar(36)                                                                                             not null comment '所属项目ID',
    created_at     datetime   default CURRENT_TIMESTAMP                                                                    null,
    updated_at     datetime   default CURRENT_TIMESTAMP                                                                    null on update CURRENT_TIMESTAMP,
    create_user_id varchar(36)                                                                                             not null comment '创建人ID',
    update_user_id varchar(36)                                                                                             not null comment '更新人ID',
    is_shared      tinyint(1) default 1                                                                                    null comment '是否共享元素(1-共享，0-私有)',
    is_deleted     tinyint(1) default 0                                                                                    not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                                                                                                null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_element_is_deleted
    on element (is_deleted);

create table environment
(
    id             int auto_increment comment '环境id'
        primary key,
    env_name       varchar(32)                        not null comment '环境名称',
    team_id        int                                not null comment '所属团队id',
    env_var        json                               null comment '环境变量列表',
    cookies        json                               null comment 'cookie列表',
    headers        json                               null comment '环境header',
    serve          json                               null comment '服务',
    dbs            json                               null comment '数据库列表',
    create_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    create_user_id int                                not null comment '创建者id',
    update_time    datetime                           null comment '更新时间',
    update_user_id int                                null comment '更新人id',
    is_deleted     tinyint  default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                           null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_environment_is_deleted
    on environment (is_deleted);

create table global_var
(
    id            int auto_increment comment 'id'
        primary key,
    team_id       int                                             not null comment '所属团队',
    type          enum ('HEADER', 'COOKIE', 'VARIABLE', 'ASSERT') null comment '参数分类',
    description   varchar(50)                                     null comment '描述',
    name          varchar(32)                                     null comment '参数名称',
    value         varchar(32)                                     null comment '参数值',
    global_assert json                                            null comment '断言数据(当type是assert的时候用)',
    disabled      tinyint default 0                               null comment '是否禁用',
    is_deleted    tinyint default 0                               not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at    datetime                                        null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_global_var_is_deleted
    on global_var (is_deleted);

create table message
(
    id            int auto_increment
        primary key,
    receiver_id   int                                not null comment '接收人ID',
    sender_id     int                                null comment '发送人ID（操作人）',
    event_type    varchar(64)                        not null comment '事件类型',
    template_code varchar(64)                        null comment '使用的模板编码',
    title         varchar(255)                       not null comment '消息标题',
    content       text                               not null comment '消息内容',
    biz_type      varchar(50)                        null comment '业务类型: bug/requirement',
    biz_id        int                                null comment '业务对象ID',
    team_id       int                                null comment '所属团队ID',
    project_id    int                                null comment '所属项目ID',
    extra_data    json                               null comment '扩展数据(JSON)',
    is_read       tinyint  default 0                 not null comment '是否已读: 0-未读 1-已读',
    read_time     datetime                           null comment '阅读时间',
    is_deleted    tinyint  default 0                 not null comment '是否已删除: 0-未删除 1-已删除',
    deleted_at    datetime                           null comment '删除时间',
    create_time   datetime default CURRENT_TIMESTAMP null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci
    comment '站内信消息表';

create index idx_biz
    on message (biz_type, biz_id);

create index idx_create_time
    on message (create_time);

create index idx_receiver_read
    on message (receiver_id, is_read);

create table message_template
(
    id               int auto_increment
        primary key,
    template_code    varchar(64)                           not null comment '模板编码，唯一标识',
    event_type       varchar(64)                           not null comment '事件类型: BUG_ASSIGNED/BUG_STATUS_CHANGED/BUG_DELETED/REQ_ASSIGNED/REQ_STATUS_CHANGED/REQ_DELETED/BUG_COMMENT_MENTION',
    channel          varchar(20) default 'SITE'            not null comment '通知渠道: SITE',
    title_template   varchar(255)                          not null comment '消息标题模板',
    content_template text                                  not null comment '消息内容模板',
    enabled          tinyint     default 1                 not null comment '是否启用: 1-启用 0-禁用',
    create_time      datetime    default CURRENT_TIMESTAMP null,
    update_time      datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_template_code
        unique (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci
    comment '消息模板表';

-- 项目级统一配置表（差量存储：无记录 = 平台默认行为，新项目零初始化）
-- config_type: NOTIFY_RULE（通知规则，key=事件类型）/ FIELD_VISIBLE（字段显隐，key=业务对象 bug/requirement/test_case）
create table project_config
(
    id             int auto_increment
        primary key,
    project_id     int                                 not null comment '所属项目',
    config_type    varchar(32)                         not null comment '配置类型：NOTIFY_RULE / FIELD_VISIBLE',
    config_key     varchar(64)                         not null comment '配置键：如 BUG_STATUS_CHANGED / bug',
    config_value   json                                null comment '配置值',
    create_user_id int                                 null comment '创建人',
    update_user_id int                                 null comment '更新人',
    create_time    datetime default CURRENT_TIMESTAMP  null,
    update_time    datetime default CURRENT_TIMESTAMP  null on update CURRENT_TIMESTAMP,
    constraint uk_project_type_key
        unique (project_id, config_type, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci
    comment '项目级统一配置（差量存储）';

create table permission
(
    id                    bigint auto_increment comment '权限ID'
        primary key,
    name                  varchar(50)                           not null comment '权限名称',
    code                  varchar(100)                          not null comment '权限编码',
    category              varchar(50)                           null comment '权限分类：platform/team/project_manage/qa/auto/report/execution',
    is_project_management tinyint     default 0                 null comment '是否项目管理类权限：1-是，0-否',
    is_project_execution  tinyint     default 0                 null comment '是否项目执行类权限：1-是，0-否',
    scope_type            varchar(20) default 'PROJECT'         not null comment '范围类型：TEAM-团队级权限，PROJECT-项目级权限',
    type                  varchar(20)                           not null comment '权限类型：MENU-菜单，BUTTON-按钮，API-接口',
    parent_id             bigint                                null comment '父权限ID',
    sort                  int         default 0                 null comment '排序',
    create_time           datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_code
        unique (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create table plan
(
    id                   int                                                                                            not null comment 'id'
        primary key,
    plan_running_setting text                                                                                           not null comment '计划运行设置',
    project_id           int                                                                                            not null comment '所属项目id',
    plan_name            varchar(255)                                                                                   not null comment '计划名称',
    cron_expression      varchar(255)                                                                                   null comment 'cron表达式',
    description          varchar(255)                                                                                   null comment '计划描述',
    execution_type       enum ('ORDER', 'PARALLEL')                                           default 'ORDER'           not null comment '场景执行类型（顺序、并发）',
    scene_status_extract int                                                                                            null comment '场景状态提取',
    params               json                                                                                           null comment '参数',
    task_type            enum ('NORMAL', 'TIMING')                                            default 'NORMAL'          not null comment '任务类型',
    status               enum ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'PAUSED') default 'NOT_STARTED'     not null comment '状态',
    create_user_id       int                                                                                            not null comment '创建人id',
    update_user_id       int                                                                                            not null comment '更新人id',
    created_at           datetime                                                             default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '创建时间',
    updated_at           datetime                                                             default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_active            tinyint(1)                                                           default 0                 not null comment '是否被激活 1：是 0：否',
    plan_category        varchar(20)                                                          default 'UI'              null comment '计划分类：UI/API/MIXED',
    webhook_enabled      tinyint(1)                                                           default 0                 not null comment '执行后是否发送 Webhook 通知：1-开启 0-关闭',
    webhook_ids          varchar(512)                                                                                   null comment '关联的Webhook配置ID，逗号分隔',
    is_deleted           tinyint                                                              default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at           datetime                                                                                       null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_plan_is_deleted
    on plan (is_deleted);

create index idx_plan_project_id
    on plan (project_id);

create table plan_webhook
(
    id             int auto_increment comment '主键ID'
        primary key,
    project_id     int                                   not null comment '所属项目ID',
    name           varchar(64)                           not null comment '配置名称（如：钉钉-测试群）',
    enabled        tinyint(1)  default 1                 not null comment '是否启用：1-启用 0-禁用',
    type           varchar(32)                           not null comment '平台类型：DINGTALK / WECHAT / FEISHU / CUSTOM',
    url            varchar(512)                          not null comment 'Webhook 请求地址',
    secret         varchar(256)                          null comment '签名密钥（钉钉/企微/飞书加签安全设置用）',
    notify_on      varchar(64) default 'SUCCESS,FAILURE' null comment '触发时机：SUCCESS（成功时）,FAILURE（失败时），逗号分隔',
    at_mobiles     varchar(256)                          null comment '@指定人手机号，多个逗号分隔（仅钉钉/企微有效）',
    create_time    datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    create_user_id int                                   null comment '创建人ID',
    update_user_id int                                   null comment '更新人ID',
    is_deleted     tinyint     default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                              null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_plan_webhook_is_deleted
    on plan_webhook (is_deleted);

create index idx_project_id
    on plan_webhook (project_id)
    comment '按项目查询索引';

create table project
(
    id                int auto_increment comment '项目id'
        primary key,
    project_name      varchar(255)                                                        not null comment '项目名称',
    description       varchar(255)                                                        null comment '描述',
    team_id           int                                                                 not null comment '所属团队id',
    update_user_id    varchar(36)                                                         not null comment '更新人ID',
    coverage          int                                                                 not null comment '覆盖率',
    status            enum ('ACTIVE', 'COMPLETED', 'SUSPENDED') default 'ACTIVE'          not null comment '状态',
    api_total         int                                                                 not null comment 'api测试用例数量',
    ui_total          int                                                                 not null comment 'UI测试场景数量',
    performance_total int                                       default 0                 not null comment '性能测试用例数量',
    plan_total        int                                       default 0                 not null comment '计划数量',
    ui_pass           int                                                                 not null comment 'UI测试报告通过率',
    tag_classify      json                                                                null comment '标签',
    created_at        datetime                                  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '创建时间',
    updated_at        datetime                                  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_user_id    varchar(36)                                                         not null comment '创建人ID',
    create_user_name  varchar(255)                                                        null comment '创建人姓名',
    owner_id          bigint                                                              null comment '项目负责人ID',
    is_deleted        tinyint                                   default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at        datetime                                                            null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_project_is_deleted
    on project (is_deleted);

create table qa_module
(
    id          int auto_increment
        primary key,
    project_id  int                                not null comment '所属项目',
    parent_id   int      default 0                 null comment '父模块ID',
    module_name varchar(100)                       not null comment '模块名称',
    sort        int      default 0                 null comment '排序',
    create_time datetime default CURRENT_TIMESTAMP null,
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_deleted  tinyint  default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at  datetime                           null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_project
    on qa_module (project_id);

create index idx_qa_module_is_deleted
    on qa_module (is_deleted);

create table report
(
    id                    int auto_increment comment '报告id'
        primary key,
    project_id            int                      not null comment '所属项目id',
    scene_number          int                      null comment '场景数量',
    step_number           int                      null comment '步骤总数',
    assert_number         int                      null comment '断言总数',
    execution_duration    double                   null comment '执行时长',
    execution_user_id     varchar(32)              not null comment '执行者id',
    execution_user_name   varchar(255)             null comment '执行者姓名',
    task_type             varchar(255)             null comment '任务类型',
    scene_error_number    int         default 0    null comment '场景执行错误数量',
    scene_success_number  int         default 0    null comment '场景执行成功数量',
    assert_success_number int         default 0    null comment '断言成功数量',
    assert_error_number   int         default 0    null comment '断言失败数量',
    assert_skip_number    int         default 0    null comment '断言跳过数量',
    create_time           datetime                 null on update CURRENT_TIMESTAMP comment '创建时间',
    end_time              datetime                 null comment '结束时间',
    status                tinyint(1)  default 0    null comment '状态',
    plan_id               int                      null comment '关联计划id',
    plan_name             varchar(255)             null comment '计划名称',
    report_name           varchar(255)             null comment '报告名称',
    video_path            json                     null comment '视频执行地址',
    scenes                longtext                 null comment '场景列表',
    step_success_number   int         default 0    null comment '步骤成功数量',
    step_error_number     int         default 0    null comment '步骤失败数量',
    step_skip_number      int         default 0    null comment '步骤跳过数量',
    report_category       varchar(20) default 'UI' null comment '报告分类：UI/API/MIXED',
    ai_summary            mediumtext               null comment 'AI 智能总结（Markdown）',
    is_deleted            tinyint     default 0    not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at            datetime                 null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_report_is_deleted
    on report (is_deleted);

create index idx_report_project_id
    on report (project_id);

create table requirement
(
    id                  int auto_increment
        primary key,
    req_code            varchar(32)                                                                                                       not null comment '需求编号 REQ-xxx',
    title               varchar(255)                                                                                                      not null comment '需求标题',
    description         text                                                                                                              null comment '需求描述',
    priority            enum ('P0', 'P1', 'P2', 'P3')                                                           default 'P2'              null comment '浼樺厛绾',
    status              enum ('DRAFT', 'REVIEWING', 'CONFIRMED', 'DEVELOPING', 'TESTING', 'RELEASED', 'CLOSED') default 'DRAFT'           null,
    project_id          int                                                                                                               not null comment '所属项目',
    module_id           int                                                                                                               null comment '所属模块',
    parent_id           int                                                                                                               null comment '父需求ID',
    req_type            varchar(20)                                                                             default 'FEATURE'         null comment '需求类型: FEATURE/BUGFIX/OPTIMIZE/TECH_DEBT',
    source              varchar(20)                                                                             default 'INTERNAL'        null comment '来源: CLIENT/INTERNAL/COMPETITOR/ONLINE',
    participants        varchar(500)                                                                                                      null comment '参与人ID(JSON数组)',
    expect_release_time datetime                                                                                                          null comment '期望上线时间',
    tags                varchar(255)                                                                                                      null comment '标签',
    version             varchar(50)                                                                                                       null comment '归属版本',
    owner_id            int                                                                                                               null comment '负责人ID',
    create_user_id      int                                                                                                               not null,
    update_user_id      int                                                                                                               null,
    create_time         datetime                                                                                default CURRENT_TIMESTAMP null,
    update_time         datetime                                                                                default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_deleted          tinyint                                                                                 default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at          datetime                                                                                                          null comment '删除时间',
    constraint uk_req_code
        unique (req_code, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_module
    on requirement (module_id);

create index idx_parent
    on requirement (parent_id);

create index idx_project_status
    on requirement (project_id, status);

create index idx_requirement_is_deleted
    on requirement (is_deleted);

create table role
(
    id          bigint auto_increment comment '角色ID'
        primary key,
    name        varchar(50)                           not null comment '角色名称',
    code        varchar(50)                           not null comment '角色编码',
    scope_type  varchar(20) default 'SYSTEM'          not null comment '范围类型：SYSTEM-内置角色，TEMPLATE-自定义模板',
    scope_id    bigint                                null comment 'TEMPLATE范围：NULL=全局模板，project.id=项目模板',
    team_id     bigint                                null comment '所属团队，NULL 表示系统预设角色',
    description varchar(255)                          null comment '角色说明',
    is_deleted  tinyint     default 0                 null comment '软删除：0-正常，1-已删除',
    deleted_at  datetime                              null comment '删除时间',
    is_system   tinyint     default 0                 null comment '是否系统预设角色：0-否，1-是',
    create_time datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_scope_code
        unique (scope_type, scope_id, code),
    constraint uk_team_code
        unique (team_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create table role_permission
(
    id            bigint auto_increment comment 'ID'
        primary key,
    role_id       bigint not null comment '角色ID',
    permission_id bigint not null comment '权限ID',
    constraint uk_role_permission
        unique (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create table scene
(
    id             int auto_increment comment '场景id'
        primary key,
    project_id     varchar(32)              null comment '所属项目id',
    parent_id      int                      not null comment '父id',
    name           varchar(255)             not null comment '场景名称',
    description    text                     null comment '场景描述',
    sort           int                      not null comment '排序',
    scene_type     enum ('SCENE', 'FOLDER') not null comment '场景类型',
    scene_setting  text                     not null comment '场景配置',
    create_at      datetime                 null on update CURRENT_TIMESTAMP comment '创建时间',
    create_user_id varchar(32)              not null comment '创建人id',
    update_user_id varchar(32)              not null comment '更新人id',
    scene_category varchar(20) default 'UI' null comment '场景分类：UI/API/MIXED',
    is_deleted     tinyint     default 0    not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                 null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_scene_is_deleted
    on scene (is_deleted);

create index idx_scene_project_id
    on scene (project_id);

create table sys_operation_log
(
    id             bigint auto_increment comment '主键'
        primary key,
    module         varchar(50)                        not null comment '所属模块：qa/automation/system',
    operate_type   varchar(50)                        not null comment '操作类型：CREATE/UPDATE/DELETE/EXECUTE/TRANSITION/LOGIN/LOGOUT/IMPORT/EXPORT/BIND/UNBIND/SORT/BATCH_DELETE',
    target_type    varchar(50)                        not null comment '对象类型：requirement/bug/testCase/testPlan/scene/plan/task/user',
    target_id      bigint                             null comment '对象ID',
    target_name    varchar(500)                       null comment '对象名称（冗余存储，便于列表展示）',
    operator_id    int                                not null comment '操作人ID',
    operator_name  varchar(50)                        null comment '操作人姓名（冗余）',
    description    mediumtext                         null comment '操作描述（字段变更JSON/创建摘要/删除前数据，可能很大）',
    request_params text                               null comment '请求参数（JSON格式，超过2000字符截断）',
    response_code  int                                null comment '响应状态码：200/500等',
    response_msg   varchar(1000)                      null comment '响应消息：成功/失败原因',
    ip             varchar(50)                        null comment '操作IP地址',
    user_agent     varchar(500)                       null comment '浏览器UA',
    duration_ms    int                                null comment '接口耗时（毫秒）',
    operate_time   datetime default CURRENT_TIMESTAMP not null comment '操作时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_module_time
    on sys_operation_log (module, operate_time);

create index idx_operator
    on sys_operation_log (operator_id, operate_time);

create index idx_target
    on sys_operation_log (target_type, target_id);

create index idx_type
    on sys_operation_log (operate_type, operate_time);

create index idx_operate_time
    on sys_operation_log (operate_time);

-- 登录日志（安全审计数据，与业务操作日志分离）
create table sys_login_log
(
    id           bigint auto_increment comment '主键'
        primary key,
    operation    varchar(20)                        not null comment '操作类型：LOGIN/LOGOUT',
    user_id      bigint                             null comment '用户ID（登录失败且用户不存在时为NULL）',
    username     varchar(100)                       null comment '登录输入的用户名',
    nickname     varchar(50)                        null comment '用户昵称（冗余）',
    status       varchar(20)                        not null comment '状态：SUCCESS/FAIL',
    message      varchar(200)                       null comment '失败原因',
    ip           varchar(50)                        null comment '登录IP',
    ip_region    varchar(100)                       null comment 'IP归属地（省/市·运营商，离线解析）',
    user_agent   varchar(500)                       null comment '浏览器UA',
    operate_time datetime default CURRENT_TIMESTAMP not null comment '操作时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_login_user
    on sys_login_log (user_id, operate_time);

create index idx_login_time
    on sys_login_log (operate_time);

create table team
(
    id             int auto_increment comment '团队id'
        primary key,
    team_name      varchar(255)                       not null comment '团队名称',
    team_number    int      default 0                 not null comment '团队人数',
    status         tinyint  default 1                 not null comment '团队状态：0-禁用，1-正常',
    is_personal    tinyint  default 0                 not null comment '是否个人团队：0-否，1-是',
    description    varchar(255)                       null comment '描述',
    created_at     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    create_user_id varchar(36)                        null comment '创建人ID',
    update_user_id varchar(36)                        null comment '更新人id',
    owner_id       bigint                             null comment '团队管理员ID（团队管理员唯一来源）',
    is_deleted     tinyint  default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                           null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_team_is_deleted
    on team (is_deleted);

create table team_member
(
    id        bigint unsigned auto_increment comment '主键'
        primary key,
    team_id   bigint unsigned                       not null comment '团队ID',
    user_id   bigint unsigned                       not null comment '用户ID',
    role_id   bigint                                null comment '角色ID',
    role      varchar(20) default 'member'          not null comment '团队内角色：admin-管理员，member-普通成员，可扩展',
    status    tinyint     default 1                 not null comment '成员状态：0-禁用（如被移除），1-正常',
    join_time datetime    default CURRENT_TIMESTAMP not null comment '加入时间',
    constraint uniq_team_user
        unique (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_role
    on team_member (role);

create index idx_team_role
    on team_member (team_id, role_id);

create index idx_user_id
    on team_member (user_id);

create table test_case
(
    id                int auto_increment
        primary key,
    case_code         varchar(32)                                                                                 not null comment '用例编号 CASE-xxx',
    case_name         varchar(255)                                                                                not null comment '用例名称',
    pre_condition     text                                                                                        null comment '前置条件',
    test_steps        json                                                                                        null comment '测试步骤 [{step:"", expected:""}, ...]',
    case_type         enum ('FUNCTION', 'API', 'PERFORMANCE', 'COMPATIBILITY', 'SMOKE') default 'FUNCTION'        null,
    priority          enum ('P0', 'P1', 'P2')                                           default 'P1'              null,
    status            enum ('DRAFT', 'REVIEWING', 'REVIEWED', 'DEPRECATED')             default 'DRAFT'           null,
    project_id        int                                                                                         not null,
    module_id         int                                                                                         null comment '所属模块',
    last_result       varchar(10)                                                                                 null comment '最近执行结果: PASS/FAIL/BLOCK/NA',
    last_execute_time datetime                                                                                    null comment '最近执行时间',
    tags              varchar(255)                                                                                null comment '标签',
    expect_duration   int                                                                                         null comment '预期执行时长(分钟)',
    requirement_id    int                                                                                         null comment '关联需求',
    folder_id         int                                                               default 0                 null comment '文件夹ID(支持树形)',
    create_user_id    int                                                                                         not null,
    update_user_id    int                                                                                         null,
    create_time       datetime                                                          default CURRENT_TIMESTAMP null,
    update_time       datetime                                                          default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_deleted        tinyint                                                           default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at        datetime                                                                                    null comment '删除时间',
    constraint uk_case_code
        unique (case_code, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_last_result
    on test_case (last_result);

create index idx_module
    on test_case (module_id);

create index idx_project_folder
    on test_case (project_id, folder_id);

create index idx_test_case_is_deleted
    on test_case (is_deleted);

create table test_case_auto_bind
(
    id           int auto_increment
        primary key,
    test_case_id int                                not null comment '文字用例ID',
    auto_type    enum ('UI_SCENE', 'API_CASE')      not null comment '自动化类型',
    auto_id      int                                not null comment 'scene.id 或 api_request.id',
    auto_name    varchar(255)                       null comment '冗余名称',
    bind_remark  varchar(500)                       null comment '绑定说明',
    create_time  datetime default CURRENT_TIMESTAMP null,
    constraint uk_bind
        unique (test_case_id, auto_type, auto_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_auto
    on test_case_auto_bind (auto_type, auto_id);

create table test_case_execution
(
    id              int auto_increment
        primary key,
    test_case_id    int                                not null comment '用例ID',
    plan_id         int                                null comment '所属计划ID',
    result          varchar(20)                        not null comment '执行结果: PASS/FAIL/BLOCK/NA',
    remark          text                               null comment '执行备注',
    execute_user_id int                                null comment '执行人',
    execute_time    datetime default CURRENT_TIMESTAMP null comment '执行时间',
    bug_id          int                                null comment '关联的BUG ID（FAIL后提Bug时回填）',
    auto_report_id  int                                null comment '关联自动化报告ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_case
    on test_case_execution (test_case_id);

create index idx_plan
    on test_case_execution (plan_id);

create index idx_time
    on test_case_execution (execute_time);

create table test_case_set
(
    id             int auto_increment comment '主键ID'
        primary key,
    project_id     int                                not null comment '所属项目',
    set_name       varchar(100)                       not null comment '测试集名称',
    description    varchar(500)                       null comment '描述',
    sort           int      default 0                 null comment '排序',
    create_user_id int                                not null comment '创建人',
    update_user_id int                                null comment '更新人',
    create_time    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_deleted     tinyint  default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                           null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci
    comment '测试集';

create index idx_is_deleted
    on test_case_set (is_deleted);

create index idx_project_id
    on test_case_set (project_id);

create table test_case_set_relation
(
    id           int auto_increment comment '主键ID'
        primary key,
    set_id       int                                not null comment '测试集ID',
    test_case_id int                                not null comment '用例ID',
    create_time  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_set_case
        unique (set_id, test_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci
    comment '测试集用例关系';

create index idx_case_id
    on test_case_set_relation (test_case_id);

create table test_plan
(
    id             int auto_increment
        primary key,
    project_id     int                                   not null comment '所属项目',
    plan_name      varchar(200)                          not null comment '计划名称',
    description    text                                  null comment '计划描述',
    status         varchar(20) default 'DRAFT'           null comment '状态: DRAFT/READY/RUNNING/COMPLETED',
    start_time     datetime                              null comment '计划开始时间',
    end_time       datetime                              null comment '计划结束时间',
    create_user_id int                                   null comment '创建人',
    create_time    datetime    default CURRENT_TIMESTAMP null,
    update_time    datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_deleted     tinyint     default 0                 not null comment '是否已删除：0-未删除，1-已删除',
    deleted_at     datetime                              null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_project
    on test_plan (project_id);

create index idx_status
    on test_plan (status);

create index idx_test_plan_is_deleted
    on test_plan (is_deleted);

create table test_plan_case
(
    id              int auto_increment
        primary key,
    plan_id         int                                   not null comment '计划ID',
    test_case_id    int                                   not null comment '用例ID',
    sort            int         default 0                 null comment '排序',
    execute_result  varchar(20) default 'UNEXECUTED'      null comment '执行结果: PASS/FAIL/BLOCK/NA/UNEXECUTED',
    execute_remark  text                                  null comment '执行备注',
    execute_user_id int                                   null comment '执行人',
    execute_time    datetime                              null comment '执行时间',
    bug_id          int                                   null comment '关联的BUG ID',
    create_time     datetime    default CURRENT_TIMESTAMP null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_case
    on test_plan_case (test_case_id);

create index idx_plan
    on test_plan_case (plan_id);

create table test_step
(
    id             int auto_increment comment '步骤ID'
        primary key,
    step_type      varchar(50)                        not null comment '步骤类型',
    step_name      varchar(255)                       not null comment '步骤名称',
    description    text                               null comment '步骤描述',
    parent_id      int      default 0                 not null comment '父步骤ID',
    order_index    int      default 0                 not null comment '执行顺序',
    project_id     varchar(36)                        not null comment '所属项目ID',
    scenario_id    varchar(36)                        not null comment '所属场景ID',
    is_disable     tinyint(1)                         not null comment '是否禁用 0：否1：是',
    step_detail    json                               not null comment '步骤详情',
    created_at     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    create_user_id varchar(36)                        not null comment '创建人ID',
    update_user_id varchar(36)                        not null comment '更新人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create table user
(
    id          bigint unsigned auto_increment comment '主键ID'
        primary key,
    username    varchar(50)                           not null comment '用户名，唯一',
    password    varchar(100)                          not null comment '加密后的密码',
    salt        varchar(50)                           not null comment '密码盐，用于加密',
    nickname    varchar(50)                           null comment '昵称',
    avatar      varchar(255)                          null comment '头像URL',
    phone       varchar(20)                           null comment '手机号',
    email       varchar(100)                          null comment '邮箱',
    status      tinyint     default 1                 not null comment '状态：0-禁用，1-正常',
    role        varchar(50) default 'user'            not null comment '全局角色：super_admin-超级管理员，user-普通用户',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uniq_username
        unique (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci;

create index idx_email
    on user (email);

create index idx_phone
    on user (phone);

create table user_role
(
    id           bigint auto_increment comment '主键'
        primary key,
    user_id      bigint                             not null comment '用户ID（关联 user.id）',
    role_id      bigint                             not null comment '角色ID（关联 role.id）',
    scope_id     bigint                             not null comment '授权范围ID：团队角色填 team.id，项目角色填 project.id',
    granted_by   bigint                             null comment '授权人ID',
    granted_time datetime default CURRENT_TIMESTAMP null comment '授权时间',
    expire_time  datetime                           null comment '过期时间，NULL 表示永久有效',
    status       tinyint  default 1                 null comment '状态：0-已撤销，1-生效中',
    constraint uk_user_role_scope
        unique (user_id, role_id, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_general_ci
    comment '用户角色授权表（支持团队级和项目级）';

create index idx_role_id
    on user_role (role_id);

create index idx_scope_id
    on user_role (scope_id);

create index idx_user_id
    on user_role (user_id);






-- 默认超级管理员：账号 admin / 密码 abc123（password 为 SaSecureUtil.aesEncrypt(salt, 明文) 结果）
INSERT INTO user (username, password, salt, nickname, role, status)
VALUES ('admin', 'gpk3WFpm7xKPC4HTwS6HeA==', 'fasttest-admin-salt-0001', '超级管理员', 'super_admin', 1)
ON DUPLICATE KEY UPDATE role = 'super_admin';


-- ----------------------------
-- 3. RBAC 默认数据
-- ----------------------------

-- 内置 SYSTEM 角色（团队管理员/团队成员/项目管理员；管理员身份硬编码放行，不挂 role_permission）
INSERT INTO role (name, code, scope_type, scope_id, team_id, description, is_system) VALUES
('团队管理员', 'team_admin',    'SYSTEM', NULL, NULL, '团队级治理，管理本团队所有内容（硬编码放行）', 1),
('团队成员',   'team_member',   'SYSTEM', NULL, NULL, '团队门票身份，本身不含任何业务权限', 1),
('项目管理员', 'project_admin', 'SYSTEM', NULL, NULL, '项目级治理，管理本项目所有内容（硬编码放行）', 1);

-- 不预置自定义项目模板角色（TEMPLATE）：由超管/团队管理员在「权限模板」中按需创建

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('团队管理', 'team', 'MENU', NULL, 10),
('团队成员管理', 'team:member:manage', 'BUTTON', 1, 11),
('团队角色管理', 'team:role:manage', 'BUTTON', 1, 12),
('团队删除', 'team:delete', 'BUTTON', 1, 13);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('项目管理', 'project', 'MENU', NULL, 20),
('项目查看', 'project:view', 'BUTTON', 4, 21),
('项目创建', 'project:create', 'BUTTON', 4, 22),
('项目编辑', 'project:update', 'BUTTON', 4, 23),
('项目删除', 'project:delete', 'BUTTON', 4, 24);

-- 项目成员管理权限（挂在「项目管理」菜单下）
INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '项目成员查看', 'project:member:view', 'BUTTON', id, 25 FROM permission WHERE code = 'project';
INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '项目成员添加', 'project:member:create', 'BUTTON', id, 26 FROM permission WHERE code = 'project';
INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '项目成员编辑', 'project:member:update', 'BUTTON', id, 27 FROM permission WHERE code = 'project';
INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '项目成员删除', 'project:member:delete', 'BUTTON', id, 28 FROM permission WHERE code = 'project';

-- 项目配置权限（项目管理员配置通知规则与字段显隐）
INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '项目配置编辑', 'project:config:update', 'BUTTON', id, 29 FROM permission WHERE code = 'project';

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('需求管理', 'qa:requirement', 'MENU', NULL, 30),
('需求查看', 'qa:requirement:view', 'BUTTON', 9, 31),
('需求创建', 'qa:requirement:create', 'BUTTON', 9, 32),
('需求编辑', 'qa:requirement:update', 'BUTTON', 9, 33),
('需求删除', 'qa:requirement:delete', 'BUTTON', 9, 34),
('需求状态流转', 'qa:requirement:transition', 'BUTTON', 9, 35);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('BUG管理', 'qa:bug', 'MENU', NULL, 40),
('BUG查看', 'qa:bug:view', 'BUTTON', 15, 41),
('BUG创建', 'qa:bug:create', 'BUTTON', 15, 42),
('BUG编辑', 'qa:bug:update', 'BUTTON', 15, 43),
('BUG删除', 'qa:bug:delete', 'BUTTON', 15, 44),
('BUG状态流转', 'qa:bug:transition', 'BUTTON', 15, 45);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('用例管理', 'qa:testcase', 'MENU', NULL, 50),
('用例查看', 'qa:testcase:view', 'BUTTON', 21, 51),
('用例创建', 'qa:testcase:create', 'BUTTON', 21, 52),
('用例编辑', 'qa:testcase:update', 'BUTTON', 21, 53),
('用例删除', 'qa:testcase:delete', 'BUTTON', 21, 54);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('测试计划管理', 'qa:testplan', 'MENU', NULL, 60),
('测试计划查看', 'qa:testplan:view', 'BUTTON', 27, 61),
('测试计划创建', 'qa:testplan:create', 'BUTTON', 27, 62),
('测试计划编辑', 'qa:testplan:update', 'BUTTON', 27, 63),
('测试计划删除', 'qa:testplan:delete', 'BUTTON', 27, 64),
('测试计划执行', 'qa:testplan:execute', 'BUTTON', 27, 65);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('UI自动化', 'auto:scene', 'MENU', NULL, 70),
('UI场景查看', 'auto:scene:view', 'BUTTON', 33, 71),
('UI场景创建', 'auto:scene:create', 'BUTTON', 33, 72),
('UI场景编辑', 'auto:scene:update', 'BUTTON', 33, 73),
('UI场景删除', 'auto:scene:delete', 'BUTTON', 33, 74),
('UI场景执行', 'auto:scene:execute', 'BUTTON', 33, 75);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('API自动化', 'auto:api', 'MENU', NULL, 80),
('API接口查看', 'auto:api:view', 'BUTTON', 39, 81),
('API接口创建', 'auto:api:create', 'BUTTON', 39, 82),
('API接口编辑', 'auto:api:update', 'BUTTON', 39, 83),
('API接口删除', 'auto:api:delete', 'BUTTON', 39, 84),
('API接口执行', 'auto:api:execute', 'BUTTON', 39, 85);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('自动化任务', 'auto:plan', 'MENU', NULL, 90),
('自动化任务查看', 'auto:plan:view', 'BUTTON', 45, 91),
('自动化任务创建', 'auto:plan:create', 'BUTTON', 45, 92),
('自动化任务编辑', 'auto:plan:update', 'BUTTON', 45, 93),
('自动化任务删除', 'auto:plan:delete', 'BUTTON', 45, 94),
('自动化任务执行', 'auto:plan:execute', 'BUTTON', 45, 95);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('测试报告', 'report', 'MENU', NULL, 100),
('测试报告查看', 'report:view', 'BUTTON', 51, 101),
('测试报告删除', 'report:delete', 'BUTTON', 51, 102);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('模块管理', 'qa:module', 'MENU', NULL, 110);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('模块查看', 'qa:module:view', 'BUTTON', LAST_INSERT_ID(), 111),
('模块创建', 'qa:module:create', 'BUTTON', LAST_INSERT_ID(), 112),
('模块编辑', 'qa:module:update', 'BUTTON', LAST_INSERT_ID(), 113),
('模块删除', 'qa:module:delete', 'BUTTON', LAST_INSERT_ID(), 114);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('BUG评论', 'qa:bug:comment', 'MENU', NULL, 130);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('评论查看', 'qa:bug:comment:view', 'BUTTON', LAST_INSERT_ID(), 131),
('评论创建', 'qa:bug:comment:create', 'BUTTON', LAST_INSERT_ID(), 132),
('评论删除', 'qa:bug:comment:delete', 'BUTTON', LAST_INSERT_ID(), 133);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('BUG操作日志', 'qa:bug:operationlog', 'MENU', NULL, 140);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('操作日志查看', 'qa:bug:operationlog:view', 'BUTTON', LAST_INSERT_ID(), 141);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('质量概览', 'qa:overview', 'MENU', NULL, 150);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('质量概览查看', 'qa:overview:view', 'BUTTON', LAST_INSERT_ID(), 151);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('UI元素库', 'auto:element', 'MENU', NULL, 160);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('元素查看', 'auto:element:view', 'BUTTON', LAST_INSERT_ID(), 161),
('元素创建', 'auto:element:create', 'BUTTON', LAST_INSERT_ID(), 162),
('元素编辑', 'auto:element:update', 'BUTTON', LAST_INSERT_ID(), 163),
('元素删除', 'auto:element:delete', 'BUTTON', LAST_INSERT_ID(), 164);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('任务Webhook', 'auto:plan:webhook', 'MENU', NULL, 170);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('Webhook查看', 'auto:plan:webhook:view', 'BUTTON', LAST_INSERT_ID(), 171),
('Webhook创建', 'auto:plan:webhook:create', 'BUTTON', LAST_INSERT_ID(), 172),
('Webhook编辑', 'auto:plan:webhook:update', 'BUTTON', LAST_INSERT_ID(), 173),
('Webhook删除', 'auto:plan:webhook:delete', 'BUTTON', LAST_INSERT_ID(), 174);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('环境管理', 'auto:env', 'MENU', NULL, 180);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('环境查看', 'auto:env:view', 'BUTTON', LAST_INSERT_ID(), 181),
('环境创建', 'auto:env:create', 'BUTTON', LAST_INSERT_ID(), 182),
('环境编辑', 'auto:env:update', 'BUTTON', LAST_INSERT_ID(), 183),
('环境删除', 'auto:env:delete', 'BUTTON', LAST_INSERT_ID(), 184);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('全局变量', 'auto:globalvar', 'MENU', NULL, 190);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('全局变量查看', 'auto:globalvar:view', 'BUTTON', LAST_INSERT_ID(), 191),
('全局变量编辑', 'auto:globalvar:update', 'BUTTON', LAST_INSERT_ID(), 193),
('全局变量删除', 'auto:globalvar:delete', 'BUTTON', LAST_INSERT_ID(), 194);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('测试步骤', 'auto:step', 'MENU', NULL, 200);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('步骤查看', 'auto:step:view', 'BUTTON', LAST_INSERT_ID(), 201),
('步骤创建', 'auto:step:create', 'BUTTON', LAST_INSERT_ID(), 202),
('步骤编辑', 'auto:step:update', 'BUTTON', LAST_INSERT_ID(), 203),
('步骤删除', 'auto:step:delete', 'BUTTON', LAST_INSERT_ID(), 204);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('自动化概览', 'auto:overview', 'MENU', NULL, 210);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('自动化概览查看', 'auto:overview:view', 'BUTTON', LAST_INSERT_ID(), 211);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('数据模板', 'auto:template', 'MENU', NULL, 220);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('数据模板-查看', 'auto:template:view', 'BUTTON', LAST_INSERT_ID(), 221),
('数据模板-创建', 'auto:template:create', 'BUTTON', LAST_INSERT_ID(), 222),
('数据模板-更新', 'auto:template:update', 'BUTTON', LAST_INSERT_ID(), 223),
('数据模板-删除', 'auto:template:delete', 'BUTTON', LAST_INSERT_ID(), 224),
('数据模板文件夹-查看', 'auto:template:folder:view', 'BUTTON', LAST_INSERT_ID(), 225),
('数据模板文件夹-创建', 'auto:template:folder:create', 'BUTTON', LAST_INSERT_ID(), 226),
('数据模板文件夹-更新', 'auto:template:folder:update', 'BUTTON', LAST_INSERT_ID(), 227),
('数据模板文件夹-删除', 'auto:template:folder:delete', 'BUTTON', LAST_INSERT_ID(), 228),
('数据模板文件夹-排序', 'auto:template:folder:sort', 'BUTTON', LAST_INSERT_ID(), 229);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('知识库', 'knowledge', 'MENU', NULL, 230);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('知识库-查看', 'knowledge:view', 'BUTTON', LAST_INSERT_ID(), 231),
('知识库-创建', 'knowledge:create', 'BUTTON', LAST_INSERT_ID(), 232),
('知识库-更新', 'knowledge:update', 'BUTTON', LAST_INSERT_ID(), 233),
('知识库-删除', 'knowledge:delete', 'BUTTON', LAST_INSERT_ID(), 234);

-- ----------------------------
-- 自定义公共函数（auto:function）
-- ----------------------------
INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('自定义函数', 'auto:function', 'MENU', NULL, 240);

INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('自定义函数-查看', 'auto:function:view', 'BUTTON', LAST_INSERT_ID(), 241),
('自定义函数-创建', 'auto:function:create', 'BUTTON', LAST_INSERT_ID(), 242),
('自定义函数-更新', 'auto:function:update', 'BUTTON', LAST_INSERT_ID(), 243),
('自定义函数-删除', 'auto:function:delete', 'BUTTON', LAST_INSERT_ID(), 244);

-- 平台级权限（仅超级管理员/平台管理员可用，团队自定义角色不可分配）
INSERT INTO permission (name, code, type, parent_id, sort) VALUES
('平台管理', 'platform', 'MENU', NULL, 5);

INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '平台团队管理', 'platform:team:manage', 'BUTTON', id, 6 FROM permission WHERE code = 'platform';

INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '平台用户管理', 'platform:user:manage', 'BUTTON', id, 7 FROM permission WHERE code = 'platform';

INSERT INTO permission (name, code, type, parent_id, sort)
SELECT '平台权限管理', 'platform:permission:manage', 'BUTTON', id, 8 FROM permission WHERE code = 'platform';

-- ----------------------------
-- 内置只读模板「项目成员」（project_member）：全项目只读，平台共享（team_id=NULL）
-- 用途：邀请成员默认角色；项目管理员变更时旧管理员无授权记录则自动降级为该角色
-- ----------------------------
INSERT INTO role (name, code, scope_type, scope_id, team_id, description, is_system)
SELECT '项目成员', 'project_member', 'TEMPLATE', NULL, NULL, '内置只读成员：全项目查看权限（系统内置，不可删改）', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'project_member' AND scope_type = 'TEMPLATE' AND team_id IS NULL);

-- 挂载全部查看类权限（不含 project:member:view——成员列表可见性由成员身份本身决定）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
JOIN permission p ON p.code LIKE '%:view' AND p.code <> 'project:member:view'
WHERE r.code = 'project_member' AND r.scope_type = 'TEMPLATE' AND r.team_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ----------------------------
-- 权限分类标记（category / 项目管理类 / 项目执行类；模板只取执行类权限 is_project_execution=1）
-- ----------------------------
UPDATE permission SET scope_type='TEAM', category='platform', is_project_management=0, is_project_execution=0
  WHERE code='platform' OR code LIKE 'platform:%';
UPDATE permission SET scope_type='TEAM', category='team', is_project_management=0, is_project_execution=0
  WHERE code='team' OR code LIKE 'team:%';
UPDATE permission SET scope_type='PROJECT', category='project_manage', is_project_management=1, is_project_execution=0
  WHERE code IN ('project','project:update','project:delete','project:member:view','project:member:create','project:member:update','project:member:delete','project:config:update');
UPDATE permission SET scope_type='TEAM', category='project_manage', is_project_management=1, is_project_execution=0
  WHERE code='project:create';
UPDATE permission SET scope_type='PROJECT', category='execution', is_project_management=0, is_project_execution=1
  WHERE code IN ('project:view');
UPDATE permission SET scope_type='PROJECT', category='qa', is_project_management=0, is_project_execution=1
  WHERE code='qa' OR code LIKE 'qa:%';
UPDATE permission SET scope_type='PROJECT', category='auto', is_project_management=0, is_project_execution=1
  WHERE code='auto' OR code LIKE 'auto:%';
UPDATE permission SET scope_type='PROJECT', category='report', is_project_management=0, is_project_execution=1
  WHERE code='report' OR code LIKE 'report:%';
UPDATE permission SET scope_type='PROJECT', category='knowledge', is_project_management=0, is_project_execution=1
  WHERE code='knowledge' OR code LIKE 'knowledge:%';
-- =============================================================
-- AI 功能（一期）表结构，2026-07-23
-- 历史环境请执行 upgrade_ai_feature.sql
-- =============================================================

create table ai_config
(
    id              bigint auto_increment comment '主键'
        primary key,
    config_name     varchar(50)            null comment '配置名（如：生产-GPT4o / 测试-DeepSeek）',
    provider        varchar(50)            not null default 'openai' comment '提供方标识（openai 兼容）',
    base_url        varchar(500)           not null comment 'OpenAI 兼容端点',
    api_key         varchar(1000)          not null default '' comment 'API Key（AES 加密存储）',
    chat_model      varchar(100)           not null comment '对话模型名',
    embedding_model varchar(100)           null comment '向量模型名，未配置则知识库降级为关键词检索',
    max_tokens      int         default 4096 not null comment '单次最大输出 tokens',
    temperature     decimal(2, 1) default 0.3 not null comment '采样温度',
    timeout_ms      int         default 60000 not null comment '请求超时（毫秒）',
    enabled         tinyint     default 0    not null comment '是否启用：0-未启用 1-启用（全表唯一生效行，服务层保证）',
    vision_enabled  tinyint     default 0    not null comment '是否启用多模态：0-否 1-是',
    remark          varchar(200)           null comment '备注（配置用途说明）',
    create_user_id  varchar(64)            null comment '创建人',
    update_user_id  varchar(64)            null comment '更新人',
    create_time     datetime               null comment '创建时间',
    update_time     datetime               null comment '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型接入配置（系统级，多档案唯一生效）';

create table ai_usage_log
(
    id             bigint auto_increment comment '主键'
        primary key,
    user_id        varchar(64)             null comment '调用用户ID',
    team_id        int                     null comment '团队ID',
    project_id     int                     null comment '项目ID（系统级调用为 NULL）',
    scene          varchar(50)             not null comment '场景：GENERATE_CASE/GENERATE_API_CASE/REPORT_SUMMARY/BUG_ANALYSIS/EMBEDDING/CONFIG_TEST',
    tokens         int                     null comment '消耗 tokens',
    duration_ms    int                     null comment '耗时（毫秒）',
    success        tinyint      default 1  not null comment '是否成功：0-失败 1-成功',
    error_msg      varchar(1000)           null comment '失败原因摘要',
    prompt_summary varchar(500)            null comment '入参摘要（不含全文）',
    create_time    datetime                null comment '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用用量日志';

create index idx_ai_usage_log_project_scene
    on ai_usage_log (project_id, scene);

create index idx_ai_usage_log_create_time
    on ai_usage_log (create_time);

create table ai_generation_record
(
    id              bigint auto_increment comment '主键'
        primary key,
    record_no       varchar(64)            not null comment '记录编号（UUID，对外暴露）',
    user_id         varchar(64)            not null comment '生成用户ID',
    team_id         int                    null comment '团队ID',
    project_id      int                    not null comment '项目ID（隔离边界）',
    scene           varchar(50)            not null comment '场景：GENERATE_CASE/GENERATE_API_CASE/REPORT_SUMMARY/BUG_CLUSTER_INSIGHT',
    entity_id       bigint                 null comment '锚定实体ID（需求ID/接口ID/报告ID；项目级场景为 NULL）',
    input_summary   text                   null comment '输入摘要（含引用的知识库 chunk 记录）',
    output_snapshot mediumtext             null comment '输出快照（JSON，追加生成时累积）',
    adopted_detail  mediumtext             null comment '采纳登记（哪些条目已入库、入库后的实体ID）',
    status          varchar(20) default 'ACTIVE' not null comment '状态：ACTIVE/EXPIRED（24h 过期）',
    create_time     datetime               null comment '创建时间',
    expire_time     datetime               null comment '过期时间',
    constraint uk_ai_generation_record_no unique (record_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 生成记录（会话锚点）';

create index idx_ai_generation_record_project_entity
    on ai_generation_record (project_id, scene, entity_id);

create table knowledge_doc
(
    id             bigint auto_increment comment '主键'
        primary key,
    project_id     int                     not null comment '所属项目（隔离边界）',
    title          varchar(200)            not null comment '文档标题',
    doc_type       varchar(20)  default 'MD' not null comment '文档类型：MD/TXT/PDF',
    content        mediumtext              null comment '文档内容（PDF 为解析后的纯文本）',
    index_status   varchar(20)  default 'PENDING' not null comment '索引状态：PENDING/INDEXING/READY/FAILED',
    cite_count     int          default 0 not null comment 'AI 引用次数（生成时检索命中累计）',
    create_user_id varchar(64)             null comment '创建人',
    update_user_id varchar(64)             null comment '更新人',
    create_time    datetime                null comment '创建时间',
    update_time    datetime                null comment '更新时间',
    is_deleted     tinyint      default 0  not null comment '是否已删除：0-未删除 1-已删除',
    deleted_at     datetime                null comment '删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档（项目级）';

create index idx_knowledge_doc_project
    on knowledge_doc (project_id);

create table knowledge_chunk
(
    id          bigint auto_increment comment '主键'
        primary key,
    doc_id      bigint                 not null comment '所属文档ID',
    project_id  int                    not null comment '所属项目（冗余，隔离边界）',
    chunk_text  text                   not null comment '分块文本',
    embedding   json                   null comment '向量（未配置向量模型时为 NULL，降级关键词检索）',
    chunk_index int         default 0  not null comment '块序号',
    token_count int                    null comment '估算 token 数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分块';

create index idx_knowledge_chunk_doc
    on knowledge_chunk (doc_id);

create index idx_knowledge_chunk_project
    on knowledge_chunk (project_id);
