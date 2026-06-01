-- ============================================================
-- HomeLab Dashboard 数据库建表脚本
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS homelab_dashboard
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE homelab_dashboard;

-- -----------------------------------------------------------
-- 1. 插件注册表（SPI 自动维护，用户仅可开关）
-- -----------------------------------------------------------
CREATE TABLE plugin (
    id                INT            AUTO_INCREMENT PRIMARY KEY,
    plugin_id         VARCHAR(64)    NOT NULL  COMMENT '插件唯一标识（代码定义，如 ikuai/pve/openwrt）',
    display_name      VARCHAR(128)   NOT NULL  COMMENT '插件显示名称',
    description       VARCHAR(512)             COMMENT '插件描述',
    icon              VARCHAR(64)              COMMENT '图标标识',
    version           VARCHAR(16)              COMMENT '插件版本',
    enabled           TINYINT(1)     DEFAULT 0 COMMENT '用户是否启用',
    status            VARCHAR(16)    DEFAULT 'offline' COMMENT 'online / offline / error',
    last_health_check DATETIME                 COMMENT '最后健康检查时间',
    created_at        DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plugin_id (plugin_id)
) ENGINE=InnoDB COMMENT='插件注册表';

-- -----------------------------------------------------------
-- 2. 插件配置表（用户通过前端填写的密钥/地址，加密存储）
-- -----------------------------------------------------------
CREATE TABLE plugin_config (
    id            INT            AUTO_INCREMENT PRIMARY KEY,
    plugin_id     VARCHAR(64)    NOT NULL  COMMENT '关联插件',
    config_key    VARCHAR(128)   NOT NULL  COMMENT '配置项 key',
    config_value  TEXT                     COMMENT '加密后的配置值',
    updated_at    DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plugin_key (plugin_id, config_key)
) ENGINE=InnoDB COMMENT='插件连接配置（地址、Token等，加密存储）';

-- -----------------------------------------------------------
-- 3. 仪表盘卡片表（用户的开关+布局操作落盘）
-- -----------------------------------------------------------
CREATE TABLE dashboard_card (
    id              INT            AUTO_INCREMENT PRIMARY KEY,
    active_template VARCHAR(64)    DEFAULT 'network-overview' COMMENT '当前激活的模板 ID',
    card_type       VARCHAR(64)    NOT NULL  COMMENT '卡片类型（代码定义，如 realtime-speed）',
    title           VARCHAR(128)   NOT NULL  COMMENT '卡片标题',
    position_x      INT            DEFAULT 0 COMMENT '网格 X 坐标（0~11）',
    position_y      INT            DEFAULT 0 COMMENT '网格 Y 坐标',
    enabled         TINYINT(1)     DEFAULT 1 COMMENT '用户是否显示此卡片',
    card_config     JSON                     COMMENT '卡片级自定义配置（如月度配额）',
    sort_order      INT            DEFAULT 0 COMMENT '排序序号',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active_template (active_template)
) ENGINE=InnoDB COMMENT='仪表盘卡片（宽高由代码定义，数据库只存位置和开关）';

-- -----------------------------------------------------------
-- 4. 全局系统设置表（键值对）
-- -----------------------------------------------------------
CREATE TABLE system_config (
    id            INT            AUTO_INCREMENT PRIMARY KEY,
    config_key    VARCHAR(128)   NOT NULL  COMMENT '配置键',
    config_value  TEXT                     COMMENT '配置值',
    value_type    VARCHAR(16)    DEFAULT 'string' COMMENT 'string / number / boolean / json',
    updated_at    DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB COMMENT='全局系统设置';

-- 默认数据
INSERT INTO system_config (config_key, config_value, value_type) VALUES
('theme',                  'dark',     'string'),
('card_theme',             'dark',     'string'),
('card_blur',              '20',       'number'),
('collect_interval',       '5000',     'number'),
('data_retention_days',    '90',       'number'),
('ws_broadcast_interval',  '1000',     'number'),
('polling_realtime_interval',  '2000',   'number'),
('polling_frequent_interval',  '30000',  'number'),
('polling_slow_interval',      '300000', 'number');

-- -----------------------------------------------------------
-- 5. 流量记账表（后端定时任务每日自动写入）
-- -----------------------------------------------------------
CREATE TABLE traffic_billing (
    id              INT            AUTO_INCREMENT PRIMARY KEY,
    plugin_id       VARCHAR(64)    NOT NULL  COMMENT '来源插件',
    record_date     DATE           NOT NULL  COMMENT '记录日期',
    download_total  BIGINT         DEFAULT 0 COMMENT '当日下行总量 (Bytes)',
    upload_total    BIGINT         DEFAULT 0 COMMENT '当日上行总量 (Bytes)',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plugin_date (plugin_id, record_date)
) ENGINE=InnoDB COMMENT='每日流量记账';

-- -----------------------------------------------------------
-- 6. 指标快照表（后端采集时按需写入）
-- -----------------------------------------------------------
CREATE TABLE metric_snapshot (
    id            INT            AUTO_INCREMENT PRIMARY KEY,
    plugin_id     VARCHAR(64)    NOT NULL  COMMENT '来源插件',
    snapshot_time DATETIME       NOT NULL  COMMENT '快照时间',
    metrics_json  JSON                     COMMENT '标准化指标快照',
    INDEX idx_plugin_time (plugin_id, snapshot_time)
) ENGINE=InnoDB COMMENT='指标历史快照';
