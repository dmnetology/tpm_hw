package com.example.demo;

import java.util.concurrent.Callable;

/**
 * Долгая задача для демонстрации отмены: имитирует обработку 10 шагами
 * по одной секунде — 10 секунд работы — и печатает прогресс, чтобы было
 * видно, на каком шаге задачу прервал cancel().
 */
public class LongRunningTask implements Callable<String> {

    private static final int TOTAL_STEPS = 10;
    private static final long STEP_MS = 1000;

    @Override
    public String call() throws Exception {
        for (int step = 1; step <= TOTAL_STEPS; step++) {
            Thread.sleep(STEP_MS);
            System.out.println("[задача] шаг " + step + "/" + TOTAL_STEPS
                    + " (поток " + Thread.currentThread().getName() + ")");
        }
        return "обработка завершена за " + TOTAL_STEPS + " с";
    }
}