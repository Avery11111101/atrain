package com.avery.atrain.service;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家即時導航管理器：管理現役導航 Session、轉乘站自動下車與目的地抵達通知
 */
public class ActiveNavigationManager {

    private final AtrainPlugin plugin;
    private final Map<UUID, ActiveNavigationSession> activeSessions = new ConcurrentHashMap<>();

    public ActiveNavigationManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public static class ActiveNavigationSession {
        private final UUID playerUuid;
        private final RoutePlannerService.RoutePlan plan;
        private int currentStepIndex = 0;

        public ActiveNavigationSession(UUID playerUuid, RoutePlannerService.RoutePlan plan) {
            this.playerUuid = playerUuid;
            this.plan = plan;
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public RoutePlannerService.RoutePlan getPlan() { return plan; }
        public int getCurrentStepIndex() { return currentStepIndex; }
        public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }

        public RoutePlannerService.RouteStep getCurrentStep() {
            if (currentStepIndex >= 0 && currentStepIndex < plan.steps().size()) {
                return plan.steps().get(currentStepIndex);
            }
            return null;
        }
    }

    public void startNavigation(Player player, RoutePlannerService.RoutePlan plan) {
        if (player == null || plan == null || !plan.found()) return;

        ActiveNavigationSession session = new ActiveNavigationSession(player.getUniqueId(), plan);
        activeSessions.put(player.getUniqueId(), session);

        var lang = plugin.getLanguageManager();
        TextUtil.send(player, lang.get(player, "nav.started", Map.of(
                "origin", plan.originStopName(),
                "destination", plan.destStopName()
        )));

        if (!plan.steps().isEmpty()) {
            var firstStep = plan.steps().get(0);
            TextUtil.send(player, lang.get(player, "nav.next_instruction", Map.of(
                    "line", firstStep.lineColor() + firstStep.lineDisplayName(),
                    "target", firstStep.toStopName()
            )));
        }
    }

    public void cancelNavigation(Player player) {
        if (player == null) return;
        if (activeSessions.remove(player.getUniqueId()) != null) {
            var lang = plugin.getLanguageManager();
            TextUtil.send(player, lang.get(player, "nav.cancelled"));
        }
    }

    public boolean hasActiveNavigation(Player player) {
        return player != null && activeSessions.containsKey(player.getUniqueId());
    }

    public ActiveNavigationSession getActiveSession(Player player) {
        return player == null ? null : activeSessions.get(player.getUniqueId());
    }

    /**
     * 當玩家進站（或停靠金磚站點）時呼叫，自動檢查轉乘站與目的地
     */
    public void onPlayerArriveAtStop(Player player, Stop stop) {
        if (player == null || stop == null) return;
        ActiveNavigationSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        RoutePlannerService.RoutePlan plan = session.getPlan();
        var lang = plugin.getLanguageManager();

        // 1. 檢測是否到達最終目的地
        if (stop.getId().equals(plan.destStopId())) {
            // 自動下車
            dismountPlayer(player);
            TextUtil.send(player, lang.get(player, "nav.destination_arrived", Map.of(
                    "stop", stop.getDisplayName()
            )));
            activeSessions.remove(player.getUniqueId());
            return;
        }

        // 2. 檢測是否到達當前階段的轉乘站點
        RoutePlannerService.RouteStep currStep = session.getCurrentStep();
        if (currStep != null && stop.getId().equals(currStep.toStopId())) {
            if (currStep.isTransferNext()) {
                // 自動下車，提醒玩家換線轉乘
                dismountPlayer(player);

                int nextIdx = session.getCurrentStepIndex() + 1;
                session.setCurrentStepIndex(nextIdx);
                var nextStep = session.getCurrentStep();

                String nextLineName = nextStep != null ? (nextStep.lineColor() + nextStep.lineDisplayName()) : "下一條路線";

                TextUtil.send(player, lang.get(player, "nav.transfer_dismount", Map.of(
                        "stop", stop.getDisplayName(),
                        "line", nextLineName
                )));

                // 發送 ActionBar 提示
                player.sendActionBar(TextUtil.component("§e[轉乘站] §f" + stop.getDisplayName() + " §7➔ 請換乘 " + nextLineName));
            } else {
                // 無需轉乘（可能同線繼續行駛）
                session.setCurrentStepIndex(session.getCurrentStepIndex() + 1);
            }
        }
    }

    private void dismountPlayer(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
    }

    public void removeSession(UUID uuid) {
        activeSessions.remove(uuid);
    }
}
