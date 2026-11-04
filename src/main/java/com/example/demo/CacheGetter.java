package com.example.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * Задача потока, работающего с singleton-кэшем: выполняет заданное число
 * обращений getOrCreate() и собирает полученные значения для проверки.
 */
public class CacheGetter implements Runnable {

    private final SingletonCache cache;
    private final int calls;

    private final List<String> results = new ArrayList<>();

    public CacheGetter(SingletonCache cache, int calls) {
        this.cache = cache;
        this.calls = calls;
    }

    @Override
    public void run() {
        for (int i = 0; i < calls; i++) {
            results.add(cache.getOrCreate());
        }
    }

    public List<String> getResults() {
        return results;
    }
}