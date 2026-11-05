package com.example.demo;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Подзадача для StructuredTaskScope (задание 14): печатает имя виртуального потока,
 * пауза имитирует I/O, возвращает строку-результат. При fail=true подзадача
 * завершается плановым сбоем раньше успешных — демонстрирует отмену группы.
 */
public class ScopeTask implements Callable<String> {

    /** Пауза успешно выполняющейся подзадачи, мс. */
    public static final int NORMAL_PAUSE_MS = 200;
    /** Пауза перед плановым сбоем, мс. */
    public static final int FAILURE_DELAY_MS = 50;
    /** Номер подзадачи с плановым сбоем в прогоне со сбоем. */
    public static final int FAILURE_ID = 50;

    private final int id;
    private final boolean fail;
    private final boolean verbose;
    private final AtomicInteger completed;

    public ScopeTask(int id, boolean fail, boolean verbose, AtomicInteger completed) {
        this.id = id;
        this.fail = fail;
        this.verbose = verbose;
        this.completed = completed;
    }

    @Override
    public String call() throws Exception {
        if (verbose) {
            System.out.println(Thread.currentThread().getName() + ": задача " + id);
        }
        if (fail) {
            Thread.sleep(FAILURE_DELAY_MS);
            throw new IllegalStateException("сбой по плану в задаче " + id);
        }
        Thread.sleep(NORMAL_PAUSE_MS);
        completed.incrementAndGet();
        return "задача " + id + " выполнена в " + Thread.currentThread().getName();
    }
}