package com.example.demo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Поток-«голодный» для моделирования starvation: пытается получить общую
 * блокировку с тайм-аутом и считает, сколько попыток провалилось. На
 * несправедливой блокировке конкурент почти всегда проскакивает вперёд —
 * ресурс достаётся редко; на справедливой блокировке очередь работает.
 */
public class StarvedWorker implements Runnable {

    private static final long TRY_TIMEOUT_MS = 20;

    private final ReentrantLock lock;

    private volatile boolean running = true;
    private int grants;
    private long failures;

    public StarvedWorker(ReentrantLock lock) {
        this.lock = lock;
    }

    @Override
    public void run() {
        try {
            while (running) {
                if (lock.tryLock(TRY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    try {
                        grants++;
                    } finally {
                        lock.unlock();
                    }
                } else {
                    failures++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить статус прерывания
        }
    }

    public void stop() {
        running = false;
    }

    public int getGrants() {
        return grants;
    }

    public long getFailures() {
        return failures;
    }
}