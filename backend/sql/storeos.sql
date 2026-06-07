-- ============================================================
-- OpenWrt 插件注册 + 配置初始化
-- ============================================================

USE homelab_dashboard;

-- 1. 注册 OpenWrt 插件
INSERT INTO plugin (plugin_id, display_name, description, icon, version, enabled, status)
VALUES ('storeos', 'OpenWrt', 'OpenWrt 路由器数据采集（系统状态、网络流量、DHCP客户端）', 'router', '1.0.0', 0, 'offline')
ON DUPLICATE KEY UPDATE display_name = 'OpenWrt';

-- 2. 注册 OpenWrt 配置（地址、账号、密码）
INSERT INTO plugin_config (plugin_id, config_key, config_value) VALUES
('storeos', 'address',  '192.168.10.10'),
('storeos', 'username', 'root'),
('storeos', 'password', '')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- 3. 卡片指标映射（每种卡片需要哪些指标 key）

-- realtime 卡片（前端高频轮询）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('speed',           'storeos.net.rx_rate',        'realtime', 'storeos'),
('speed',           'storeos.net.tx_rate',        'realtime', 'storeos'),
('realtimeSpeed',   'storeos.net.rx_rate',        'realtime', 'storeos'),
('realtimeSpeed',   'storeos.net.tx_rate',        'realtime', 'storeos'),
('systemStats',     'storeos.conn.count',         'realtime', 'storeos')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- frequent 卡片（中频轮询）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('systemStats',     'storeos.memory.used',        'frequent', 'storeos'),
('systemStats',     'storeos.memory.total',       'frequent', 'storeos'),
('systemStats',     'storeos.memory.usage',       'frequent', 'storeos'),
('systemStats',     'storeos.load.1',             'frequent', 'storeos'),
('systemStats',     'storeos.load.5',             'frequent', 'storeos'),
('systemStats',     'storeos.load.15',            'frequent', 'storeos'),
('deviceList',      'storeos.dhcp.count',         'frequent', 'storeos'),
('deviceList',      'storeos.process.count',      'frequent', 'storeos')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- slow 卡片（低频轮询）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('systemOverview',  'storeos.system.hostname',    'slow', 'storeos'),
('systemOverview',  'storeos.system.version',     'slow', 'storeos'),
('systemOverview',  'storeos.system.model',       'slow', 'storeos'),
('systemOverview',  'storeos.system.uptime',      'slow', 'storeos'),
('traffic',         'storeos.traffic.total_rx',   'slow', 'storeos'),
('traffic',         'storeos.traffic.total_tx',   'slow', 'storeos'),
('systemStats',     'storeos.disk.used',          'slow', 'storeos'),
('systemStats',     'storeos.disk.total',         'slow', 'storeos'),
('systemStats',     'storeos.disk.usage',         'slow', 'storeos'),
('wanStatus',       'storeos.net.interfaces',     'slow', 'storeos')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- OpenWrt 专属卡片映射

-- storeosNodeStatus: 内存 + 负载 + 连接数
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('storeosNodeStatus',  'storeos.memory.used',        'frequent', 'storeos'),
('storeosNodeStatus',  'storeos.memory.total',       'frequent', 'storeos'),
('storeosNodeStatus',  'storeos.memory.usage',       'frequent', 'storeos'),
('storeosNodeStatus',  'storeos.load.1',             'frequent', 'storeos'),
('storeosNodeStatus',  'storeos.load.5',             'frequent', 'storeos'),
('storeosNodeStatus',  'storeos.load.15',            'frequent', 'storeos'),
('storeosNodeStatus',  'storeos.conn.count',         'realtime', 'storeos')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- storeosSystemInfo: 设备信息 + 磁盘 + 运行时间
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('storeosSystemInfo',  'storeos.system.hostname',    'slow', 'storeos'),
('storeosSystemInfo',  'storeos.system.version',     'slow', 'storeos'),
('storeosSystemInfo',  'storeos.system.model',       'slow', 'storeos'),
('storeosSystemInfo',  'storeos.system.uptime',      'slow', 'storeos'),
('storeosSystemInfo',  'storeos.disk.used',          'slow', 'storeos'),
('storeosSystemInfo',  'storeos.disk.total',         'slow', 'storeos'),
('storeosSystemInfo',  'storeos.disk.usage',         'slow', 'storeos')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- storeosRealtimeTraffic: 网络速率 + 连接数
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('storeosRealtimeTraffic', 'storeos.net.rx_rate',    'realtime', 'storeos'),
('storeosRealtimeTraffic', 'storeos.net.tx_rate',    'realtime', 'storeos'),
('storeosRealtimeTraffic', 'storeos.conn.count',     'realtime', 'storeos')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);
