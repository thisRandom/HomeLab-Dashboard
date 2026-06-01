package com.homelab.dashboard_backend.service;

import org.springframework.stereotype.Service;

/**
 * 管理仪表盘编辑模式状态（内存级，单用户场景）
 * 进入编辑模式时记录 template，退出时清除。
 * 编辑模式下 MetricsController 返回全量指标，方便预览面板展示所有卡片。
 */
@Service
public class DashboardEditingService {

    private volatile String editingTemplate = null;

    public void startEditing(String template) {
        this.editingTemplate = template;
    }

    public void stopEditing() {
        this.editingTemplate = null;
    }

    /**
     * 判断指定 template 是否处于编辑模式
     */
    public boolean isEditing(String template) {
        return template != null && template.equals(editingTemplate);
    }

    public String getEditingTemplate() {
        return editingTemplate;
    }
}
