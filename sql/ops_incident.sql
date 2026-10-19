-- ============================================================
-- OpsPilot 故障事件表结构
-- 阶段一「事件状态机」存储底座：OPEN（故障进行中） -> CLOSED（已恢复关闭）
-- 用法：
--   1) 全新环境 / 仅看结构：直接执行「一、建表语句」
--   2) 已有 ops_incident 表：跳过建表，执行「二、增量升级 ALTER」
-- 注意：
--   - event_no 由应用层生成（UUID / 雪花），本表只约束唯一
--   - updated_at 是 DB 级 ON UPDATE，每次 UPDATE 自动刷新，
--     因此「首次失败时间」必须单独用 first_failed_at 字段存，不能复用 updated_at
--   - idx_service_status 是状态机 find-or-create 查询的命门：
--     按 (service_name, status) 定位「该服务当前未关闭的事件」，避免全表扫描
-- ============================================================

# -- 一、建表语句（新环境 / 文档基准）
# CREATE TABLE ops_incident (
#     id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
#     event_no        VARCHAR(64)   NOT NULL                COMMENT '故障事件编号（业务唯一，应用层生成）',
#     service_name    VARCHAR(128)  NOT NULL                COMMENT '发生故障的目标服务名',
#     service_version VARCHAR(64)   NULL                    COMMENT '故障服务版本（可选）',
#     event_type      VARCHAR(64)   NOT NULL                COMMENT '故障类型：REMOTE_CALL_TIMEOUT / REMOTE_CALL_ERROR / CIRCUIT_OPEN',
#     severity        VARCHAR(32)   NULL     DEFAULT 'MEDIUM' COMMENT '严重度：LOW / MEDIUM / HIGH / CRITICAL',
#     description     VARCHAR(1024) NULL     DEFAULT ''      COMMENT '故障描述（含 traceId 与错误摘要）',
#     status          VARCHAR(32)   NOT NULL DEFAULT 'OPEN'  COMMENT '状态：OPEN=故障进行中 / CLOSED=已恢复关闭',
#     fail_count      INT           NOT NULL DEFAULT 1       COMMENT '连续失败次数（持续失败每次 +1）',
#     first_failed_at DATETIME      NULL                    COMMENT '首次失败时间（仅创建时写入，不随后续失败刷新）',
#     resolved_at     DATETIME      NULL                    COMMENT '恢复关闭时间（恢复时写入）',
#     created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
#     updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
#     PRIMARY KEY (id),
#     UNIQUE KEY uk_event_no (event_no),
#     KEY idx_service_status (service_name, status),
#     KEY idx_status (status)
# ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
#   COMMENT='故障事件表（事件状态机：OPEN->CLOSED）';

-- 二、增量升级 ALTER（已有表时执行；索引若已存在请去掉对应 ADD KEY 行）
ALTER TABLE ops_incident
    MODIFY COLUMN status          VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN=故障进行中 / CLOSED=已恢复关闭',
    ADD    COLUMN fail_count      INT         NOT NULL DEFAULT 1 COMMENT '连续失败次数' AFTER status,
    ADD    COLUMN first_failed_at DATETIME    NULL                COMMENT '首次失败时间' AFTER fail_count,
    ADD    COLUMN resolved_at     DATETIME    NULL                COMMENT '恢复关闭时间' AFTER first_failed_at,
    ADD    KEY idx_service_status (service_name, status),
    ADD    KEY idx_status (status);

-- 若库中已有历史 DETECTED 状态数据，统一为 OPEN（演示库可选，不影响新建）
-- UPDATE ops_incident SET status = 'OPEN' WHERE status = 'DETECTED';
