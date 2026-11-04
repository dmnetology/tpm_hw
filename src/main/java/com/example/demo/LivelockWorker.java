package com.example.demo;

/**
 * Задача потока для моделирования livelock: владея общим ресурсом, поток
 * уступает его голодному соседу. Лимит уступчивости задаёт выход из
 * бесконечных циклов: при большом лимите поток уступает всегда — это livelock
 * (оба активны, но прогресса нет); при ограниченном лимите после нескольких
 * уступок подряд поток выполняет задачу сам.
 */
public class LivelockWorker implements Runnable {

    private static final long WORK_MS = 20;

    private final String name;
    private final String other;
    private final SharedResource resource;
    private final int politenessLimit;

    private volatile boolean running = true;
    private int consecutiveYields; // уступки подряд — сбрасываются после выполнения задачи
    private int totalTransfers;
    private int meals;

    public LivelockWorker(String name, String other, SharedResource resource, int politenessLimit) {
        this.name = name;
        this.other = other;
        this.resource = resource;
        this.politenessLimit = politenessLimit;
    }

    @Override
    public void run() {
        try {
            while (running) {
                if (resource.getOwner().equals(name)) {
                    if (resource.isHungry(other) && consecutiveYields < politenessLimit) {
                        resource.setOwner(other); // «уступаю соседу» — состояние меняется, задачи нет
                        consecutiveYields++;
                        totalTransfers++;
                    } else {
                        resource.setHungry(name, false); // выполнение задачи: «поел»
                        meals++;
                        consecutiveYields = 0; // счёт уступок подряд обнулён — лимит отсчитывается заново
                        Thread.sleep(WORK_MS);
                    }
                } else {
                    Thread.sleep(5); // ресурс у соседа — ждём своей очереди
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить статус прерывания
        }
    }

    public void stop() {
        running = false;
    }

    public int getTotalTransfers() {
        return totalTransfers;
    }

    public int getMeals() {
        return meals;
    }
}