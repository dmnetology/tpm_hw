package com.example.demo;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Слушатель-счётчик: ведёт статистику по типам событий в ConcurrentHashMap.
 * Инкремент через атомарный merge — составная операция «проверить и увеличить»
 * без него была бы неатомарна даже в ConcurrentHashMap.
 */
public class StatsListener implements UserEventListener {

    private final ConcurrentHashMap<String, Integer> countsByType;

    public StatsListener(ConcurrentHashMap<String, Integer> countsByType) {
        this.countsByType = countsByType;
    }

    @Override
    public void onEvent(UserEvent event) {
        countsByType.merge(event.type(), 1, Integer::sum);
    }
}