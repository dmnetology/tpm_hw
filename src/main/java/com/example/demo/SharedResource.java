package com.example.demo;

import java.util.HashSet;
import java.util.Set;

/**
 * Общий ресурс для моделирования livelock: два потока постоянно передают его
 * друг другу, каждый уступает «голодному» соседу. Владение постоянно меняется
 * (потоки активны), но реального прогресса — выполнения задачи — нет.
 */
public class SharedResource {

    private String owner;
    private final Set<String> hungry = new HashSet<>();

    public SharedResource(String firstOwner) {
        owner = firstOwner;
    }

    public synchronized String getOwner() {
        return owner;
    }

    public synchronized void setOwner(String owner) {
        this.owner = owner;
    }

    public synchronized void setHungry(String who, boolean isHungry) {
        if (isHungry) {
            hungry.add(who);
        } else {
            hungry.remove(who);
        }
    }

    public synchronized boolean isHungry(String who) {
        return hungry.contains(who);
    }
}