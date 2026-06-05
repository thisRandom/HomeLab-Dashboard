-- ============================================================
-- PVE 插件注册 + 配置初始化
-- ============================================================

USE homelab_dashboard;

-- 1. 注册 PVE 插件
INSERT INTO plugin (plugin_id, display_name, description, icon, version, enabled, status)
VALUES ('pve', 'Proxmox VE', 'Proxmox 虚拟化平台监控（节点状态、虚拟机/容器管理）', 'server', '1.0.0', 0, 'offline')
ON DUPLICATE KEY UPDATE display_name = 'Proxmox VE';

-- 2. 注册 PVE 配置（地址、节点、Token）
--    请根据实际环境修改以下配置
INSERT INTO plugin_config (plugin_id, config_key, config_value) VALUES
('pve', 'host',         '192.168.10.254'),
('pve', 'port',         '8006'),
('pve', 'node',         'pve'),
('pve', 'username',     'root'),
('pve', 'realm',        'pam'),
('pve', 'token_id',     'testing'),
('pve', 'token_secret', '2fe44ac5-7fb0-4ef5-b191-15fa8cdcda9c'),
('pve', 'ssh_host',     '192.168.10.254'),
('pve', 'ssh_port',     '22'),
('pve', 'ssh_user',     'root')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- 3. 卡片指标映射（每种卡片需要哪些指标 key）

-- realtime 卡片（前端高频轮询）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('pveNodeStatus',  'pve.cpu.usage',          'realtime', 'pve'),
('pveNodeStatus',  'pve.memory.used',        'realtime', 'pve'),
('pveNodeStatus',  'pve.memory.total',       'realtime', 'pve'),
('pveNodeStatus',  'pve.memory.usage',       'realtime', 'pve'),
('pveNodeStatus',  'pve.loadavg.1',          'realtime', 'pve'),
('pveNodeStatus',  'pve.loadavg.5',          'realtime', 'pve'),
('pveNodeStatus',  'pve.loadavg.15',         'realtime', 'pve'),
('pveTempCard',    'cpu.temp',               'realtime', 'pve')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- frequent 卡片（中频轮询）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('pveVmList',      'pve.vm.count',           'frequent', 'pve'),
('pveVmList',      'pve.ct.count',           'frequent', 'pve'),
('pveVmList',      'pve.vm.running_count',   'frequent', 'pve'),
('pveVmList',      'pve.vm.list',            'frequent', 'pve'),
('pveVmList',      'pve.vm.details',         'frequent', 'pve')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);

-- slow 卡片（低频轮询）
INSERT INTO card_metric_mapping (card_type, metric_key, poll_freq, plugin_id) VALUES
('pveNodeInfo',    'pve.system.uptime',      'slow', 'pve'),
('pveNodeInfo',    'pve.cpu.cores',          'slow', 'pve'),
('pveNodeInfo',    'pve.cpu.model',          'slow', 'pve'),
('pveNodeInfo',    'pve.disk.used',          'slow', 'pve'),
('pveNodeInfo',    'pve.disk.total',         'slow', 'pve'),
('pveNodeInfo',    'pve.disk.usage',         'slow', 'pve'),
('pveNodeInfo',    'pve.system.node',        'slow', 'pve'),
('pveNodeInfo',    'pve.system.status',      'slow', 'pve')
ON DUPLICATE KEY UPDATE poll_freq = VALUES(poll_freq);
