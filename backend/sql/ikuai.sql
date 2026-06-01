-- ============================================================
-- iKuai 插件注册 + 配置初始化
-- ============================================================

USE homelab_dashboard;

-- 1. 注册 iKuai 插件
INSERT INTO plugin (plugin_id, display_name, description, icon, version, enabled, status)
VALUES ('ikuai', 'iKuai Router', 'iKuai 路由器数据采集（系统状态、在线设备、WAN接口）', 'router', '1.0.0', 1, 'offline')
ON DUPLICATE KEY UPDATE display_name = 'iKuai Router';

-- 2. 注册 iKuai 配置（地址、账号、密码）
INSERT INTO plugin_config (plugin_id, config_key, config_value) VALUES
('ikuai', 'address',  'http://192.168.10.1'),
('ikuai', 'username', 'admin'),
('ikuai', 'password', 'Wjh070228')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
