package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.mapper.DashboardCardMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardCardMapper cardMapper;

    public DashboardController(DashboardCardMapper cardMapper) {
        this.cardMapper = cardMapper;
    }

    @GetMapping("/cards")
    public List<DashboardCardMapper.DashboardCard> getCards(
            @RequestParam(defaultValue = "network-overview") String template) {
        return cardMapper.selectByTemplate(template);
    }

    @PostMapping("/cards")
    public String saveCards(@RequestBody Map<String, Object> body) {
        String template = (String) body.getOrDefault("template", "network-overview");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) body.get("cards");

        // Clear existing cards for this template
        cardMapper.deleteByTemplate(template);

        // Insert new cards
        if (cards != null) {
            for (int i = 0; i < cards.size(); i++) {
                Map<String, Object> c = cards.get(i);
                cardMapper.insert(DashboardCardMapper.DashboardCard.builder()
                        .activeTemplate(template)
                        .cardType((String) c.get("type"))
                        .title((String) c.get("title"))
                        .positionX(toInt(c.get("x")))
                        .positionY(toInt(c.get("y")))
                        .enabled(true)
                        .sortOrder(i)
                        .build());
            }
        }
        return "ok";
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 0;
    }
}
