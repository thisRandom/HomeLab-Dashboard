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

-- 3. 卡片指标映射（每种卡片需要哪些指标 key）
--    修改卡片时需同步更新此处

-- realtime 卡片
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('speed',           'stream.upload',          'realtime', 'ikuai'),
('speed',           'stream.download',        'realtime', 'ikuai'),
('temperature',     'cpu.temp',               'realtime', 'ikuai'),
('cpuTemp',         'cpu.temp',               'realtime', 'ikuai'),
('wanStatus',       'wan.*.status',           'realtime', 'ikuai'),
('wanStatus',       'wan.*.upload',           'realtime', 'ikuai'),
('wanStatus',       'wan.*.download',         'realtime', 'ikuai'),
('systemStats',     'cpu.usage',              'realtime', 'ikuai'),
('systemStats',     'stream.connect_num',     'realtime', 'ikuai'),
('realtimeSpeed',   'stream.upload',          'realtime', 'ikuai'),
('realtimeSpeed',   'stream.download',        'realtime', 'ikuai'),
('realtimeSpeed',   'cpu.usage',              'realtime', 'ikuai'),
('realtimeSpeed',   'stream.connect_num',     'realtime', 'ikuai')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- frequent 卡片（后端 collectFrequent 采集）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('deviceTraffic',   'lan.device_details',     'frequent', 'ikuai'),
('deviceList',      'lan.device_count',       'frequent', 'ikuai'),
('deviceList',      'lan.devices',            'frequent', 'ikuai')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- slow 卡片（后端 collectSlow 采集）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('systemStats',     'memory.used',            'slow', 'ikuai'),
('systemStats',     'memory.total',           'slow', 'ikuai'),
('systemStats',     'memory.usage',           'slow', 'ikuai'),
('traffic',         'stream.total_up',        'slow', 'ikuai'),
('traffic',         'stream.total_down',      'slow', 'ikuai'),
('uptime',          'system.uptime',          'slow', 'ikuai'),
('systemOverview',  'system.hostname',        'slow', 'ikuai'),
('systemOverview',  'system.version',         'slow', 'ikuai'),
('systemOverview',  'system.uptime',          'slow', 'ikuai'),
('systemOverview',  'online_user.count',     'slow', 'ikuai')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);
