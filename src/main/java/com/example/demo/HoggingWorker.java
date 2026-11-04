package com.example.demo;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Поток-«жадный» для моделирования starvation: в плотном цикле снова и снова
 * захватывает блокировку и почти не оставляет конкурентам шансов получить
 * ресурс. На несправедливой блокировке он проскакивает в очередь; на
 * справедливой — встаёт в общую очередь наравне с конкурентами.
 */
public class HoggingWorker implements Runnable {

    private static final int HOLD_TICKS = 300_000;

    private final ReentrantLock lock;

    private volatile boolean running = true;
    private int grants;

    public HoggingWorker(ReentrantLock lock) {
        this.lock = lock;
    }

    @Override
    public void run() {
        while (running) {
            lock.lock();
            try {
                for (int i = 0; i < HOLD_TICKS; i++) {
                    Thread.onSpinWait(); // держит замок короткую, но заметную долю времени
                }
            } finally {
                lock.unlock();
            }
            grants++;
        }
    }

    public void stop() {
        running = false;
    }

    public int getGrants() {
        return grants;
    }
}