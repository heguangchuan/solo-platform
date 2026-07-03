-- =====================================================
-- Solo Platform Database Initialization Script
-- Database: solo_db
-- Date: 2026-06-19
-- =====================================================

-- 创建数据库（如果不存在）
CREATE
DATABASE IF NOT EXISTS solo_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE
solo_db;

SET
FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- AI 模块表
-- =====================================================

-- AI素材表
DROP TABLE IF EXISTS ai_asset;
CREATE TABLE ai_asset
(
    id            bigint                                                        NOT NULL COMMENT '素材ID',
    user_id       bigint                                                        NOT NULL COMMENT '用户ID',
    task_id       bigint                                                                 DEFAULT NULL COMMENT '关联任务ID',
    asset_type    varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '素材类型: IMAGE/VIDEO/AUDIO/DOCUMENT',
    file_name     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
    file_url      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件访问URL',
    cover_url     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '封面图URL',
    thumbnail_url varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '缩略图URL',
    mime_type     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT 'MIME类型',
    ext           varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '扩展名',
    size          bigint                                                                 DEFAULT NULL COMMENT '文件大小(字节)',
    duration      int                                                                    DEFAULT NULL COMMENT '时长(秒，音视频)',
    width         int                                                                    DEFAULT NULL COMMENT '宽度(像素，图片视频)',
    height        int                                                                    DEFAULT NULL COMMENT '高度(像素，图片视频)',
    prompt        text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '生成指令',
    model_name    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '生成模型',
    status        tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0无效 1有效',
    remark        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '备注',
    deleted       tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time   datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY           idx_user_id (user_id),
    KEY           idx_task_id (task_id),
    KEY           idx_asset_type (asset_type),
    KEY           idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI素材表';

-- AI会话表
DROP TABLE IF EXISTS ai_conversation;
CREATE TABLE ai_conversation
(
    id                bigint                                                       NOT NULL COMMENT '会话ID',
    user_id           bigint                                                       NOT NULL COMMENT '用户ID',
    title             varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '会话标题',
    conversation_type varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CHAT' COMMENT '会话类型: CHAT对话/CREATION创作/TASK任务',
    model_config_id   bigint                                                                DEFAULT NULL COMMENT '使用的模型配置ID',
    last_message_time datetime                                                              DEFAULT NULL COMMENT '最后一条消息时间',
    message_count     int                                                          NOT NULL DEFAULT '0' COMMENT '消息总数',
    deleted           tinyint                                                      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time       datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY               idx_user_id (user_id),
    KEY               idx_conversation_type (conversation_type),
    KEY               idx_last_message_time (last_message_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI会话表';

-- AI创作任务表
DROP TABLE IF EXISTS ai_generation_task;
CREATE TABLE ai_generation_task
(
    id              bigint                                                       NOT NULL COMMENT '任务ID',
    user_id         bigint                                                       NOT NULL COMMENT '用户ID',
    conversation_id bigint                                                                DEFAULT NULL COMMENT '关联会话ID',
    message_id      bigint                                                                DEFAULT NULL COMMENT '关联消息ID',
    task_type       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型: TEXT/IMAGE/VIDEO/AUDIO',
    prompt          text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户原始输入',
    enhanced_prompt text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '优化后的提示词',
    template_id     bigint                                                                DEFAULT NULL COMMENT '使用的模板ID',
    model_config_id bigint                                                                DEFAULT NULL COMMENT '使用的模型配置ID',
    model_name      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '使用的模型名称',
    model_params    text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '模型参数JSON',
    status          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING待处理/PROCESSING处理中/SUCCESS成功/FAILED失败/CANCELLED已取消',
    result_type     varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '结果类型: TEXT/URL/FILE',
    result_content  longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '结果内容（文本或URL）',
    error_msg       varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        DEFAULT NULL COMMENT '错误信息',
    tokens          int                                                                   DEFAULT NULL COMMENT '消耗Token数',
    cost            decimal(10, 6)                                                        DEFAULT NULL COMMENT '估算成本',
    start_time      datetime                                                              DEFAULT NULL COMMENT '开始执行时间',
    finish_time     datetime                                                              DEFAULT NULL COMMENT '完成时间',
    deleted         tinyint                                                      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time     datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY             idx_user_id (user_id),
    KEY             idx_conversation_id (conversation_id),
    KEY             idx_task_type (task_type),
    KEY             idx_status (status),
    KEY             idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI创作任务表';

-- AI供应商异步任务表
DROP TABLE IF EXISTS ai_provider_task;
CREATE TABLE ai_provider_task
(
    id                 bigint                                                       NOT NULL COMMENT '供应商任务ID',
    generation_task_id bigint                                                       NOT NULL COMMENT '本地创作任务ID',
    conversation_id    bigint                                                                DEFAULT NULL COMMENT '关联会话ID',
    user_id            bigint                                                       NOT NULL COMMENT '用户ID',
    username           varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '用户名',
    task_type          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型: VIDEO等',
    provider           varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '模型来源',
    model_name         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '模型名称',
    model_config_id    bigint                                                                DEFAULT NULL COMMENT '模型配置ID',
    provider_task_id   varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '供应商任务ID',
    provider_video_id  varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '供应商视频ID',
    provider_status    varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '供应商任务状态',
    progress           int                                                                   DEFAULT NULL COMMENT '进度百分比',
    result_url         varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        DEFAULT NULL COMMENT '供应商结果URL',
    error_msg          varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        DEFAULT NULL COMMENT '错误信息',
    seconds            varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '视频时长',
    size               varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '视频尺寸',
    start_time         datetime                                                              DEFAULT NULL COMMENT '开始时间',
    finish_time        datetime                                                              DEFAULT NULL COMMENT '完成时间',
    next_check_time    datetime                                                              DEFAULT NULL COMMENT '下次查询时间',
    deleted            tinyint                                                      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time        datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY                idx_generation_task_id (generation_task_id),
    KEY                idx_provider_video_id (provider_video_id),
    KEY                idx_status_next_check (provider_status, next_check_time),
    KEY                idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI供应商异步任务表';

-- AI消息表
DROP TABLE IF EXISTS ai_message;
CREATE TABLE ai_message
(
    id                bigint                                                       NOT NULL COMMENT '消息ID',
    conversation_id   bigint                                                       NOT NULL COMMENT '会话ID',
    role              varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色: USER用户/ASSISTANT助手/SYSTEM系统/TOOL工具',
    content           longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
    message_type      varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT/IMAGE/VIDEO/AUDIO',
    model_name        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '使用的模型名称',
    tokens            int                                                                   DEFAULT NULL COMMENT '消耗Token数',
    prompt_tokens     int                                                                   DEFAULT NULL COMMENT '输入Token数',
    completion_tokens int                                                                   DEFAULT NULL COMMENT '输出Token数',
    cost              decimal(10, 6)                                                        DEFAULT NULL COMMENT '估算成本',
    error_msg         varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        DEFAULT NULL COMMENT '错误信息',
    attachments       text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '附件列表JSON: [{"type":"IMAGE","url":"...","mimeType":"image/png","fileName":"...","assetId":"..."}]',
    metadata          text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '元数据JSON',
    deleted           tinyint                                                      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time       datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY               idx_conversation_id (conversation_id),
    KEY               idx_role (role),
    KEY               idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI消息表';

-- AI模型配置表
DROP TABLE IF EXISTS ai_model_config;
CREATE TABLE ai_model_config
(
    id             bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    provider       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '模型来源',
    model_name     varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '模型名称',
    model_type     varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '模型类型: TEXT/IMAGE/VIDEO/EMBEDDING/AUDIO',
    api_key        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API密钥',
    base_url       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT 'API基础地址',
    model_params   text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '模型参数配置JSON',
    support_vision tinyint                                                       NOT NULL DEFAULT '0' COMMENT '是否支持图片输入: 0否 1是',
    status         tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    sort           int                                                           NOT NULL DEFAULT '1' COMMENT '排序',
    deleted        tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time    datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY            idx_provider (provider),
    KEY            idx_model_type (model_type),
    KEY            idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

-- 初始化模型配置示例数据：不内置真实 API Key，默认禁用，需管理员配置密钥后启用
INSERT INTO ai_model_config (id, provider, model_name, model_type, api_key, base_url, model_params, support_vision, status, sort, deleted, create_time, update_time)
VALUES (2067812965232103426, 'agnes', 'agnes-1.5-flash', 'TEXT',
        '', 'https://apihub.agnes-ai.com/v1/chat/completions',
        '{"temperature":0.5,"max_tokens":1024}', 1, 0, 1, 0,
        '2026-06-19 11:33:24', '2026-06-19 11:33:24'),
       (2067812965232103427, 'agnes', 'agnes-2.0-flash', 'TEXT',
        '', 'https://apihub.agnes-ai.com/v1/chat/completions',
        '{"temperature":0.5,"max_tokens":1024}', 1, 0, 1, 0,
        '2026-06-19 11:33:24', '2026-06-19 11:35:11'),
       (2067812965232103428, 'agnes', 'agnes-image-2.0-flash', 'IMAGE',
        '', 'https://apihub.agnes-ai.com/v1/images/generations', '', 0, 0, 1, 0,
        '2026-06-19 11:33:24', '2026-06-19 11:35:11'),
       (2067812965232103429, 'agnes', 'agnes-image-2.1-flash', 'IMAGE',
        '', 'https://apihub.agnes-ai.com/v1/images/generations', '', 0, 0, 1, 0,
        '2026-06-19 11:33:24', '2026-06-19 11:35:11'),
       (2067816917453459458, 'agnes', 'agnes-video-v2.0', 'VIDEO',
        '', 'https://apihub.agnes-ai.com/v1/videos', '', 0, 0, 1, 0,
        '2026-06-19 11:49:06', '2026-06-19 11:49:06');

-- 提示词模板表
DROP TABLE IF EXISTS ai_prompt_template;
CREATE TABLE ai_prompt_template
(
    id            bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    template_name varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
    template_code varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板编码',
    template_type varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '模板类型: CHAT/COPYWRITING/IMAGE/VIDEO/TRANSLATION',
    content       text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板内容（支持占位符）',
    variables     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '变量定义JSON',
    example       text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '示例输出',
    status        tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    sort          int                                                           NOT NULL DEFAULT '1' COMMENT '排序',
    remark        varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '备注',
    deleted       tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time   datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_code (template_code),
    KEY           idx_template_type (template_type),
    KEY           idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词模板表';

-- 初始化提示词模板数据
INSERT INTO ai_prompt_template (id, template_name, template_code, template_type, content, variables, example, status,
                                sort, remark, deleted, create_time, update_time)
VALUES (1, '小红书文案', 'xiaohongshu', 'COPYWRITING',
        '#角色\n你是一位资深的小红书博主，擅长撰写吸引人的种草文案。\n\n#目标\n请根据以下信息撰写一篇小红书风格的文案。\n\n#产品/内容信息\n{{content}}\n\n#风格要求\n1. 使用生动、亲切的语气\n2. 适当使用 emoji 增加趣味性\n3. 开头要有吸引力\n4. 分点清晰，易于阅读\n5. 结尾加上相关标签',
        NULL, NULL, 1, 1, NULL, 0, '2026-06-19 11:01:56', '2026-06-19 11:01:56'),
       (2, '抖音脚本', 'douyin_script', 'COPYWRITING',
        '#角色\n你是一位专业的短视频脚本策划师，熟悉抖音平台的内容创作规律。\n\n#目标\n请根据以下主题创作一个15-60秒的抖音短视频脚本。\n\n#主题\n{{content}}\n\n#脚本要求\n1. 开头3秒必须抓住眼球\n2. 内容节奏要快\n3. 要有明确的卖点或观点\n4. 结尾要有引导互动或关注的话术\n5. 包含：镜头描述、台词、时长、背景音乐建议',
        NULL, NULL, 1, 2, NULL, 0, '2026-06-19 11:01:56', '2026-06-19 11:01:56'),
       (3, '广告语创作', 'slogan', 'COPYWRITING',
        '#角色\n你是一位资深广告文案策划，擅长创作令人难忘的品牌广告语。\n\n#目标\n请根据以下品牌信息创作5条广告语。\n\n#品牌信息\n{{content}}\n\n#要求\n1. 简洁有力，易于记忆\n2. 突出品牌核心价值\n3. 富有创意和感染力\n4. 适合各种传播渠道',
        NULL, NULL, 1, 3, NULL, 0, '2026-06-19 11:01:56', '2026-06-19 11:01:56'),
       (4, '图片生成提示词', 'image_prompt', 'IMAGE',
        '#角色\n你是一位专业的 AI 绘画提示词专家，擅长将用户简单的描述转化为详细的、高质量的图片生成提示词。\n\n#目标\n请根据以下描述，优化并扩展成适合 AI 绘画模型的详细提示词。\n\n#用户描述\n{{content}}\n\n#优化要求\n1. 增加画面细节描述\n2. 指定艺术风格\n3. 补充光照、材质、构图等信息\n4. 使用英文输出（适配大多数图片模型）\n5. 包含负向提示词',
        NULL, NULL, 1, 4, NULL, 0, '2026-06-19 11:01:56', '2026-06-19 11:01:56'),
       (5, '普通对话', 'chat', 'CHAT', '{{content}}', NULL, NULL, 1, 5, NULL, 0, '2026-06-19 11:01:56',
        '2026-06-19 11:01:56');

-- =====================================================
-- 系统模块表
-- =====================================================

-- 系统参数表
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config
(
    id           bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '参数ID',
    config_name  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数名称',
    config_key   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数键',
    config_value varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数值',
    remark       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '备注',
    deleted      tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key),
    KEY          idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数表';

-- 字典数据表
DROP TABLE IF EXISTS sys_dict_data;
CREATE TABLE sys_dict_data
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
    dict_type   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
    dict_label  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典标签',
    dict_value  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典键值',
    sort        int                                                           NOT NULL DEFAULT '1' COMMENT '排序',
    status      tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted     tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY         idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

-- 初始化字典数据
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort, status, deleted, create_time, update_time)
VALUES (2066820557811949570, 'sys_file_types', 'mp4', '1', 2, 1, 0, '2026-06-16 17:49:55', '2026-06-16 17:49:55'),
       (2066820617408815105, 'sys_file_types', 'png', '2', 1, 1, 0, '2026-06-16 17:49:10', '2026-06-16 17:50:10');

-- 字典类型表
DROP TABLE IF EXISTS sys_dict_type;
CREATE TABLE sys_dict_type
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
    dict_name   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称',
    dict_type   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
    status      tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted     tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (dict_type),
    KEY         idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- 文件管理表
DROP TABLE IF EXISTS sys_file;
CREATE TABLE sys_file
(
    id            bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    original_name varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传时的原始文件名',
    storage_name  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径名（含哈希，避免重名）',
    url           varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '可访问URL（相对路径或绝对URL）',
    ext           varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '扩展名（小写，不带点）',
    mime_type     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT 'Content-Type',
    size          bigint                                                        NOT NULL COMMENT '字节数',
    uploader_id   bigint                                                                 DEFAULT NULL COMMENT '上传人ID',
    uploader_name varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '上传人账号',
    deleted       tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time   datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY           idx_uploader (uploader_id),
    KEY           idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件管理表';

-- 服务器管理表
DROP TABLE IF EXISTS sys_server;
CREATE TABLE sys_server
(
    id                 bigint                                                        NOT NULL COMMENT '服务器ID',
    server_name        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务器名称',
    host               varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主机地址，IP或域名',
    port               int                                                           NOT NULL DEFAULT 22 COMMENT 'SSH端口',
    username           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
    auth_type          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'PASSWORD' COMMENT '认证方式: PASSWORD密码 PRIVATE_KEY密钥',
    password_secret    varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '密码密文',
    private_key_secret text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '私钥密文',
    os_type            varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '操作系统类型',
    status             tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0停用 1启用',
    description        varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '描述',
    deleted            tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_name (server_name),
    KEY                idx_host (host),
    KEY                idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器管理表';

-- 操作日志表
DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log
(
    id              bigint                                                       NOT NULL COMMENT '日志ID',
    module          varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块名',
    business_type   varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型: ADD/EDIT/DELETE/EXPORT/IMPORT/LOGIN/LOGOUT/OTHER',
    description     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '操作描述',
    request_method  varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT 'HTTP方法',
    request_url     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '请求URL',
    request_param   text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '请求参数JSON',
    response_status tinyint                                                      NOT NULL DEFAULT '1' COMMENT '响应状态: 0失败 1成功',
    error_msg       varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        DEFAULT NULL COMMENT '错误信息',
    cost_ms         bigint                                                                DEFAULT NULL COMMENT '耗时(毫秒)',
    operator_id     bigint                                                                DEFAULT NULL COMMENT '操作人ID',
    operator_name   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '操作人用户名',
    client_source   varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '客户端来源: web/app/...',
    client_ip       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '客户端IP',
    user_agent      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT 'User-Agent',
    create_time     datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY             idx_create_time (create_time),
    KEY             idx_operator_name (operator_name),
    KEY             idx_module_type (module, business_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 系统消息表
DROP TABLE IF EXISTS sys_message;
CREATE TABLE sys_message
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    username    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '接收用户名',
    title       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息标题',
    content     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
    type        varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT 'SYSTEM' COMMENT '消息类型: SYSTEM系统通知,NOTICE公告,REMINDER提醒',
    level       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT 'INFO' COMMENT '消息级别: INFO普通,WARNING警告,ERROR错误,IMPORTANT重要',
    read_flag   tinyint                                                       NOT NULL DEFAULT '0' COMMENT '是否已读: 0未读 1已读',
    route       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '跳转路由',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY         idx_username (username),
    KEY         idx_read_flag (read_flag),
    KEY         idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统消息表';

-- 系统权限表
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission
(
    id              bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    permission_name varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
    permission_code varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
    url             varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '关联URL',
    type            tinyint                                                       NOT NULL COMMENT '类型: 1菜单 2按钮',
    parent_id       bigint                                                        NOT NULL DEFAULT '0' COMMENT '父级ID',
    sort            int                                                           NOT NULL DEFAULT '0' COMMENT '排序',
    icon            varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '图标',
    status          tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted         tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY             idx_permission_code (permission_code),
    KEY             idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

-- 初始化权限数据
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
VALUES
-- 一级菜单
(1, '系统管理', 'system', NULL, 1, 0, 1, 'Setting', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(2, '系统设置', 'setting', NULL, 1, 0, 2, 'Tools', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(3, '测试管理', 'test:manage', '/test/manage', 1, 0, 3, 'Operation', 1, 0, '2026-06-16 09:00:00',
 '2026-06-16 09:00:00'),
(4, '任务调度', 'task', NULL, 1, 0, 4, 'Timer', 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(5, 'AI创作', 'ai', NULL, 1, 0, 5, 'Magic', 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
-- 系统管理子菜单
(6, '用户管理', 'system:user', '/system/user', 1, 1, 1, 'User', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(7, '角色管理', 'system:role', '/system/role', 1, 1, 2, 'UserFilled', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(8, '权限管理', 'system:permission', '/system/permission', 1, 1, 3, 'Lock', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(9, '日志管理', 'system:log', '/system/log', 1, 1, 4, 'Document', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(10, '在线用户', 'system:online', '/system/online', 1, 1, 5, 'Connection', 1, 0, '2026-06-16 09:00:00',
 '2026-06-16 09:00:00'),
(11, '文件管理', 'system:file', '/system/file', 1, 1, 6, 'Files', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
-- 用户管理按钮
(12, '用户查询', 'system:user:list', NULL, 2, 6, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(13, '用户新增', 'system:user:add', NULL, 2, 6, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(14, '用户编辑', 'system:user:edit', NULL, 2, 6, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(15, '用户删除', 'system:user:delete', NULL, 2, 6, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(16, '用户启停', 'system:user:switch', NULL, 2, 6, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(17, '用户授权', 'system:user:r2u', NULL, 2, 6, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(18, '用户导入', 'system:user:import', NULL, 2, 6, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(19, '用户导出', 'system:user:export', NULL, 2, 6, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(20, '用户分页列表', 'system:user:page', '', 2, 6, 9, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:24'),
(21, '获取用户信息', 'user/info', '', 2, 6, 10, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:23'),
-- 角色管理按钮
(22, '角色查询', 'system:role:list', NULL, 2, 7, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(23, '角色新增', 'system:role:add', NULL, 2, 7, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(24, '角色编辑', 'system:role:edit', NULL, 2, 7, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(25, '角色删除', 'system:role:delete', NULL, 2, 7, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(26, '角色授权', 'system:role:p2r', NULL, 2, 7, 5, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(27, '角色导入', 'system:role:import', NULL, 2, 7, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(28, '角色导出', 'system:role:export', NULL, 2, 7, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(29, '角色分页列表', 'system:role:page', '', 2, 7, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:25'),
-- 权限管理按钮
(30, '权限查询', 'system:permission:list', '', 2, 8, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:28'),
(31, '权限新增', 'system:permission:add', '', 2, 8, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:02'),
(32, '权限编辑', 'system:permission:edit', '', 2, 8, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:03'),
(33, '权限删除', 'system:permission:delete', '', 2, 8, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:04'),
-- 日志管理按钮
(34, '日志查询', 'system:log:list', NULL, 2, 9, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(35, '日志删除', 'system:log:delete', NULL, 2, 9, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(36, '日志导出', 'system:log:export', NULL, 2, 9, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
-- 在线用户按钮
(37, '在线查询', 'system:online:list', NULL, 2, 10, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(38, '强制下线', 'system:online:kick', NULL, 2, 10, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
-- 文件管理按钮
(39, '文件查询', 'system:file:list', NULL, 2, 11, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(40, '文件上传', 'system:file:upload', NULL, 2, 11, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(41, '文件删除', 'system:file:delete', NULL, 2, 11, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
-- 任务调度子菜单
(42, '任务管理', 'task:manage', '/task/manage', 1, 4, 1, 'Timer', 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(43, '执行日志', 'task:log', '/task/log', 1, 4, 2, 'Document', 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
-- 任务管理按钮
(44, '任务查询', 'task:manage:list', NULL, 2, 42, 1, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(45, '任务新增', 'task:manage:add', NULL, 2, 42, 2, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(46, '任务编辑', 'task:manage:edit', NULL, 2, 42, 3, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(47, '任务删除', 'task:manage:delete', NULL, 2, 42, 4, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(48, '任务启停', 'task:manage:switch', NULL, 2, 42, 5, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(49, '任务执行', 'task:manage:run', NULL, 2, 42, 6, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(50, '任务日志查询', 'task:log:list', NULL, 2, 43, 1, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
-- 系统设置子菜单
(51, '参数管理', 'setting:config', '/setting/config', 1, 2, 1, 'SetUp', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(52, '字典管理', 'setting:dict', '/setting/dict', 1, 2, 2, 'Collection', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
-- 参数管理按钮
(53, '参数查询', 'setting:config:list', NULL, 2, 51, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(54, '参数新增', 'setting:config:add', NULL, 2, 51, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(55, '参数编辑', 'setting:config:edit', NULL, 2, 51, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(56, '参数删除', 'setting:config:delete', NULL, 2, 51, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(57, '参数导出', 'setting:config:export', NULL, 2, 51, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
-- 字典管理按钮
(58, '字典类型查询', 'setting:dict:type:list', NULL, 2, 52, 1, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(59, '字典类型新增', 'setting:dict:type:add', NULL, 2, 52, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(60, '字典类型编辑', 'setting:dict:type:edit', NULL, 2, 52, 3, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(61, '字典类型删除', 'setting:dict:type:delete', NULL, 2, 52, 4, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(62, '字典数据查询', 'setting:dict:data:list', NULL, 2, 52, 5, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(63, '字典数据新增', 'setting:dict:data:add', NULL, 2, 52, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(64, '字典数据编辑', 'setting:dict:data:edit', NULL, 2, 52, 7, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(65, '字典数据删除', 'setting:dict:data:delete', NULL, 2, 52, 8, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
-- AI创作子菜单
(66, '对话创作', 'ai:conversation', '/ai/conversation', 1, 5, 3, 'ChatDotRound', 1, 0, '2026-06-18 09:00:00',
 '2026-06-19 11:03:46'),
(67, '创作历史', 'ai:history', '/ai/history', 1, 5, 6, 'Clock', 1, 0, '2026-06-18 09:00:00', '2026-06-19 11:03:46'),
(90, '图片创作', 'ai:imageCreation', '/ai/image-creation', 1, 5, 4, 'Picture', 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
(94, '视频创作', 'ai:videoCreation', '/ai/video-creation', 1, 5, 5, 'VideoCamera', 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
-- 对话创作按钮
(68, '新建会话', 'ai:conversation:create', NULL, 2, 66, 1, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(69, '发送消息', 'ai:conversation:send', NULL, 2, 66, 2, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(70, '查看会话', 'ai:conversation:list', NULL, 2, 66, 3, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(71, '编辑会话', 'ai:conversation:edit', NULL, 2, 66, 4, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(72, '删除会话', 'ai:conversation:delete', NULL, 2, 66, 5, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
-- 图片创作按钮
(91, '生成图片', 'ai:imageCreation:generate', NULL, 2, 90, 1, NULL, 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
(92, '图片会话查看', 'ai:imageCreation:list', NULL, 2, 90, 2, NULL, 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
(93, '图片会话删除', 'ai:imageCreation:delete', NULL, 2, 90, 3, NULL, 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
-- 视频创作按钮
(95, '生成视频', 'ai:videoCreation:generate', NULL, 2, 94, 1, NULL, 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
(96, '视频会话查看', 'ai:videoCreation:list', NULL, 2, 94, 2, NULL, 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
(97, '视频会话删除', 'ai:videoCreation:delete', NULL, 2, 94, 3, NULL, 1, 0, '2026-06-20 00:00:00', '2026-06-20 00:00:00'),
-- 创作历史按钮
(73, '历史查询', 'ai:history:list', NULL, 2, 67, 1, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(74, '历史详情', 'ai:history:detail', NULL, 2, 67, 2, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
-- AI管理新增菜单
(75, '模型配置', 'ai:modelConfig', '/ai/model-config', 1, 5, 1, 'Setting', 1, 0, '2026-06-19 11:01:57',
 '2026-06-19 11:03:46'),
(76, '提示词模板', 'ai:promptTemplate', '/ai/prompt-template', 1, 5, 2, 'Document', 1, 0, '2026-06-19 11:01:57',
 '2026-06-19 11:03:46'),
(77, '素材管理', 'ai:asset', '/ai/asset', 1, 5, 5, 'Picture', 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:03:46'),
-- 模型配置按钮
(78, '模型配置查看', 'ai:modelConfig:list', NULL, 2, 75, 1, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
(79, '模型配置新增', 'ai:modelConfig:add', NULL, 2, 75, 2, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
(80, '模型配置编辑', 'ai:modelConfig:edit', NULL, 2, 75, 3, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
(81, '模型配置删除', 'ai:modelConfig:delete', NULL, 2, 75, 4, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
-- 提示词模板按钮
(82, '提示词模板查看', 'ai:promptTemplate:list', NULL, 2, 76, 1, NULL, 1, 0, '2026-06-19 11:01:57',
 '2026-06-19 11:01:57'),
(83, '提示词模板新增', 'ai:promptTemplate:add', NULL, 2, 76, 2, NULL, 1, 0, '2026-06-19 11:01:57',
 '2026-06-19 11:01:57'),
(84, '提示词模板编辑', 'ai:promptTemplate:edit', NULL, 2, 76, 3, NULL, 1, 0, '2026-06-19 11:01:57',
 '2026-06-19 11:01:57'),
(85, '提示词模板删除', 'ai:promptTemplate:delete', NULL, 2, 76, 4, NULL, 1, 0, '2026-06-19 11:01:57',
 '2026-06-19 11:01:57'),
-- 素材管理按钮
(86, '素材管理查看', 'ai:asset:list', NULL, 2, 77, 1, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
(87, '素材管理新增', 'ai:asset:add', NULL, 2, 77, 2, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
(88, '素材管理编辑', 'ai:asset:edit', NULL, 2, 77, 3, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
(89, '素材管理删除', 'ai:asset:delete', NULL, 2, 77, 4, NULL, 1, 0, '2026-06-19 11:01:57', '2026-06-19 11:01:57'),
-- GB/T 28181 国标视频
(98, '国标视频', 'gb28181', NULL, 1, 0, 6, 'VideoCamera', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(99,  '设备列表', 'gb28181:device',         '/gb28181/device',         1, 98, 1, 'Monitor',    1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(100, '通道列表', 'gb28181:device-channel', '/gb28181/device-channel', 1, 98, 2, 'Connection', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
-- 国标设备按钮
(101, '设备列表查询', 'gb28181:device:list',   NULL, 2, 99, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(102, '设备目录同步', 'gb28181:device:sync',   NULL, 2, 99, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(103, '设备删除',     'gb28181:device:delete', NULL, 2, 99, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
-- 国标通道按钮
(104, '通道列表查询', 'gb28181:device-channel:list', NULL, 2, 100, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
-- 流媒体节点 / 实时点播（Sprint 2）
(105, '流媒体节点', 'gb28181:media-server', '/gb28181/media-server', 1, 98, 3, 'Cpu', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(106, '实时点播', 'gb28181:play',          '/gb28181/play',          1, 98, 4, 'VideoPlay',     1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(107, '节点列表查询', 'gb28181:media-server:list', NULL, 2, 105, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(108, '节点保存',     'gb28181:media-server:save', NULL, 2, 105, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(109, '节点删除',     'gb28181:media-server:delete', NULL, 2, 105, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(110, '开始点播', 'gb28181:play:start', NULL, 2, 106, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(111, '停止点播', 'gb28181:play:stop',  NULL, 2, 106, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(112, '历史回放', 'gb28181:playback',        '/gb28181/playback', 1, 98,  5, 'VideoCamera', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(113, '录像查询', 'gb28181:playback:query',   NULL, 2, 112, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(114, '开始回放', 'gb28181:playback:start',   NULL, 2, 112, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(115, '停止回放', 'gb28181:playback:stop',    NULL, 2, 112, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(116, '回放控制', 'gb28181:playback:control', NULL, 2, 112, 4, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
-- 服务器管理菜单
(125, '服务器管理', 'server', '/system/server', 1, 1, 7, 'Monitor', 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
-- 服务器管理按钮
(126, '服务器查询', 'server:list', NULL, 2, 125, 1, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(127, '服务器新增', 'server:add', NULL, 2, 125, 2, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(128, '服务器编辑', 'server:edit', NULL, 2, 125, 3, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(129, '服务器删除', 'server:delete', NULL, 2, 125, 4, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(130, 'Web终端', 'server:terminal', NULL, 2, 125, 5, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(131, '文件浏览', 'server:file:list', NULL, 2, 125, 6, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(132, '文件上传', 'server:file:upload', NULL, 2, 125, 7, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(133, '文件下载', 'server:file:download', NULL, 2, 125, 8, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(134, '文件删除', 'server:file:delete', NULL, 2, 125, 9, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');

-- 系统角色表
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_code   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '角色编码',
    role_name   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
    description varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '角色描述',
    status      tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted     tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    KEY         idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- 初始化角色数据
INSERT INTO sys_role (id, role_code, role_name, description, status, deleted, create_time, update_time)
VALUES (1, 'ADMIN', '超级管理员', '拥有所有权限', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
       (2, 'USER', '普通用户', '基础用户角色', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

-- 角色权限关联表
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission
(
    id            bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id       bigint NOT NULL COMMENT '角色ID',
    permission_id bigint NOT NULL COMMENT '权限ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY           idx_role_id (role_id),
    KEY           idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 初始化超级管理员权限（使用 INSERT SELECT 方式，将所有权限分配给超级管理员角色）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE deleted = 0;

-- 动态任务表
DROP TABLE IF EXISTS sys_task;
CREATE TABLE sys_task
(
    id           bigint                                                        NOT NULL COMMENT '任务ID',
    task_name    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
    task_code    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
    cron         varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Cron表达式',
    handler_name varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务处理器名称',
    task_params  text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '任务参数',
    status       tinyint                                                       NOT NULL DEFAULT '0' COMMENT '状态: 0停用 1启用',
    remark       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '备注',
    deleted      tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_code (task_code),
    KEY          idx_status (status),
    KEY          idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态任务表';

-- 初始化任务数据
INSERT INTO sys_task (id, task_name, task_code, cron, handler_name, task_params, status, remark, deleted, create_time,
                      update_time)
VALUES (2067200000000000001, '示例动态任务', 'example.dynamic.task', '0/30 * * * * ?', 'exampleTask',
        '{"source":"init"}', 0, '启用后每30秒写入一条执行日志', 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00');

-- 动态任务执行日志表
DROP TABLE IF EXISTS sys_task_log;
CREATE TABLE sys_task_log
(
    id             bigint                                                        NOT NULL COMMENT '日志ID',
    task_id        bigint                                                        NOT NULL COMMENT '任务ID',
    task_name      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
    task_code      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
    handler_name   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务处理器名称',
    task_params    text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '任务参数',
    execution_time datetime                                                      NOT NULL COMMENT '执行时间',
    duration_ms    bigint                                                         DEFAULT NULL COMMENT '执行耗时(毫秒)',
    result_status  tinyint                                                       NOT NULL COMMENT '执行结果: 0失败 1成功',
    error_msg      varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
    PRIMARY KEY (id),
    KEY            idx_task_id (task_id),
    KEY            idx_task_code (task_code),
    KEY            idx_execution_time (execution_time),
    KEY            idx_result_status (result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态任务执行日志表';

-- 系统用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user
(
    id                 bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username           varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '用户名',
    password           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MD5加密密码',
    nickname           varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '昵称',
    email              varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '邮箱',
    phone              varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '手机号',
    avatar             varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '头像URL',
    status             tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    pwd_reset_required tinyint                                                       NOT NULL DEFAULT '0' COMMENT '是否首次登录强制改密: 0否 1是',
    deleted            tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY                idx_username (username),
    KEY                idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 初始化用户数据（默认密码：admin123，MD5加密：0192023a7bbd73250516f069df18b500）
INSERT INTO sys_user (id, username, password, nickname, email, phone, avatar, status, pwd_reset_required, deleted,
                      create_time, update_time)
VALUES (1, 'admin', '0192023a7bbd73250516f069df18b500', '管理员', NULL, NULL, NULL, 1, 0, 0, '2026-06-14 22:35:02',
        '2026-06-14 22:35:02');

-- 用户角色关联表
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role
(
    id      bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id bigint NOT NULL COMMENT '用户ID',
    role_id bigint NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY     idx_user_id (user_id),
    KEY     idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 初始化用户角色关联
INSERT INTO sys_user_role (id, user_id, role_id)
VALUES (1, 1, 1);

SET
FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 注：GB/T 28181 国标模块的所有表（gb_*）已拆分到 sql/gb28181.sql
-- 启用国标功能时再单独执行该脚本，按模块解耦。
-- =====================================================
