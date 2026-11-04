package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации Fork/Join Framework. Подсчёт количества
 * агрегированных элементов каталога из прошлых заданий — товаров с ценой
 * выше порога — на массиве из 20 млн цен выполняется тремя способами:
 * последовательно (база), рекурсивной задачей RecursiveTask с делением
 * пополам (fork/join) и разметкой на пачки, где каждая пачка — новая задача,
 * отправленная в пул submit(). Пул собственный (не commonPool), с именованными
 * воркерами; после каждого параллельного прогона печатается число краж задач.
 */
@Service
public class ForkJoinDemoService {

    private static final int CATALOG_SIZE = 50_000_000;
    private static final double PRICE_MIN = 10_000;
    private static final double PRICE_MAX = 200_000;
    private static final double COUNT_THRESHOLD = 90_000; // порог агрегации: цена выше него
    private static final int SPLIT_THRESHOLD = 4_000_000; // пачка: до 4 млн цен

    /** Демонстрация Fork/Join, вызывается при старте приложения. */
    public void runForkJoinDemo() {
        System.out.println("=== Подготовка каталога ===");
        System.out.println("Генерируется каталог из " + CATALOG_SIZE + " цен...");
        double[] prices = generateCatalog();
        System.out.println("Каталог сгенерирован: " + CATALOG_SIZE + " цен, порог агрегации — дороже "
                + (long) COUNT_THRESHOLD + " руб.");
        long sequentialMs = runSequentialCounting(prices);
        long recursiveMs = runRecursiveTask(prices);
        long batchMs = runBatchTasks(prices);
        System.out.printf("Время: последовательно %d мс, рекурсивно %d мс, батчами %d мс"
                + " — на простом подсчёте накладные расходы Fork/Join сопоставимы с работой%n",
                sequentialMs, recursiveMs, batchMs);
    }

    /** Цены товаров в рублях — данные агрегации из прошлых заданий. */
    private double[] generateCatalog() {
        double[] prices = new double[CATALOG_SIZE];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < prices.length; i++) {
            prices[i] = random.nextDouble(PRICE_MIN, PRICE_MAX + 1);
        }
        return prices;
    }

    /** Этап 1. Последовательный подсчёт в одном цикле — база для сравнения. */
    private long runSequentialCounting(double[] prices) {
        System.out.println("=== Последовательный подсчёт ===");
        long start = System.nanoTime();
        long found = 0;
        for (double price : prices) {
            if (price > COUNT_THRESHOLD) {
                found++;
            }
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Найдено " + found + " из " + prices.length + " товаров за " + elapsed + " мс");
        return elapsed;
    }

    /** Этап 2. Рекурсивная задача: деление диапазона пополам, fork/join на каждой ступени. */
    private long runRecursiveTask(double[] prices) {
        System.out.println("=== Рекурсивная задача RecursiveTask: деление пополам ===");
        ForkJoinPool pool = createPool();
        try {
            AtomicInteger taskCounter = new AtomicInteger(); // считаем созданные пачки-задачи
            CountingTask root = new CountingTask(prices, 0, prices.length, SPLIT_THRESHOLD,
                    COUNT_THRESHOLD, taskCounter);
            long start = System.nanoTime();
            long found = pool.invoke(root);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            System.out.println("Рекурсивно найдено " + found + " товаров за " + elapsed
                    + " мс, пачек-задач в дереве — " + taskCounter.get());
            System.out.println("Краж задач воркерами: " + pool.getStealCount());
            return elapsed;
        } finally {
            pool.shutdown();
        }
    }

    /** Этап 3. Батчи: каталог размечается на пачки, каждая пачка — новая задача submit(). */
    private long runBatchTasks(double[] prices) {
        System.out.println("=== Батчи: каждая пачка — новая задача ===");
        ForkJoinPool pool = createPool();
        try {
            int batchCount = (prices.length + SPLIT_THRESHOLD - 1) / SPLIT_THRESHOLD;
            List<BatchCountTask> batches = new ArrayList<>();
            for (int i = 0; i < batchCount; i++) {
                int from = i * SPLIT_THRESHOLD;
                int to = Math.min(from + SPLIT_THRESHOLD, prices.length);
                batches.add(new BatchCountTask(prices, from, to, COUNT_THRESHOLD));
            }
            System.out.println("Каталог разбит на " + batchCount + " пачек по не более "
                    + SPLIT_THRESHOLD + " цен, все отправлены в пул");
            long start = System.nanoTime();
            List<ForkJoinTask<Long>> futures = batches.stream().map(pool::submit).toList();
            long found = 0;
            for (ForkJoinTask<Long> future : futures) {
                found += future.join(); // ждём по очереди: пул работает параллельно
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            System.out.println("Батчами найдено " + found + " товаров за " + elapsed + " мс");
            System.out.println("Краж задач воркерами: " + pool.getStealCount());
            return elapsed;
        } finally {
            pool.shutdown();
        }
    }

    /** Собственный ForkJoinPool: по числу ядер, именованные воркеры, LIFO-режим для рекурсии. */
    private ForkJoinPool createPool() {
        int parallelism = Runtime.getRuntime().availableProcessors();
        AtomicInteger workerSeq = new AtomicInteger();
        System.out.println("Свой ForkJoinPool: parallelism = " + parallelism
                + " (по числу ядер), воркеры fj-worker-N, asyncMode = false (LIFO для рекурсии)");
        return new ForkJoinPool(
                parallelism,
                pool -> {
                    ForkJoinWorkerThread thread =
                            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    thread.setName("fj-worker-" + workerSeq.incrementAndGet());
                    return thread;
                },
                (thread, throwable) -> {
                    System.out.println("Ошибка в воркере: " + throwable);
                },
                false);
    }
}