package com.avery.atrain.train;

import org.bukkit.entity.Minecart;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrainTaskRegistry {
    private static final Map<UUID, TrainMovementTask> tasks = new ConcurrentHashMap<>();

    public static void register(Minecart cart, TrainMovementTask task) {
        if (cart != null) tasks.put(cart.getUniqueId(), task);
    }

    public static void unregister(Minecart cart) {
        if (cart != null) tasks.remove(cart.getUniqueId());
    }

    public static TrainMovementTask get(Minecart cart) {
        return cart == null ? null : tasks.get(cart.getUniqueId());
    }

    public static void cancel(Minecart cart) {
        if (cart == null) return;
        TrainMovementTask task = tasks.remove(cart.getUniqueId());
        if (task != null) task.cancel();
    }

    public static void cancelAllTasks() {
        for (TrainMovementTask task : new java.util.ArrayList<>(tasks.values())) {
            task.cancel();
        }
        tasks.clear();
    }

    public void cancelAll() {
        for (TrainMovementTask task : tasks.values()) task.cancel();
        tasks.clear();
    }

    public void shutdownAll() {
        for (TrainMovementTask task : new java.util.ArrayList<>(tasks.values())) {
            task.forceEnd();
        }
        tasks.clear();
    }
}
