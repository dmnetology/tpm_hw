package com.example.demo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Задача из задания 14: печатает имя своего потока (Thread.currentThread().getName())
 * и имитирует неблокирующую I/O операцию паузой Thread.sleep().
 */
public class IoEmulationTask implements Runnable {

    /** Длительность имитации I/O одной задачи, мс. */
    public static final int IO_PAUSE_MS = 10;

    private final int id;
    private final boolean verbose;
    private final AtomicInteger completed;

    public IoEmulationTask(int id, boolean verbose, AtomicInteger completed) {
        this.id = id;
        this.verbose = verbose;
        this.completed = completed;
    }

    @Override
    public void run() {
        if (verbose) {
            System.out.println(Thread.currentThread().getName() + ": задача " + id);
        }
        try {
            Thread.sleep(IO_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            completed.incrementAndGet();
        }
    }
}