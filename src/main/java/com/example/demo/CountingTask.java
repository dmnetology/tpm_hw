package com.example.demo;

import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Рекурсивная задача Fork/Join: подсчёт количества товаров дороже порога
 * в своём диапазоне массива цен. Диапазон делится пополам, пока не станет
 * не больше порога разбиения; каждая маленькая пачка — новая задача:
 * левая уходит fork(), правая считается в текущем потоке, результат
 * складывается после join().
 */
public class CountingTask extends RecursiveTask<Long> {

    private final double[] prices;
    private final int from; // включительно
    private final int to;   // не включительно
    private final int threshold;
    private final double priceThreshold;
    private final AtomicInteger taskCounter;

    public CountingTask(double[] prices, int from, int to, int threshold,
                        double priceThreshold, AtomicInteger taskCounter) {
        this.prices = prices;
        this.from = from;
        this.to = to;
        this.threshold = threshold;
        this.priceThreshold = priceThreshold;
        this.taskCounter = taskCounter;
    }

    @Override
    protected Long compute() {
        taskCounter.incrementAndGet(); // каждая созданная пачка — новая задача
        int size = to - from;
        if (size <= threshold) {
            return countLeaf();
        }
        int mid = from + size / 2;
        CountingTask left = new CountingTask(prices, from, mid, threshold, priceThreshold, taskCounter);
        CountingTask right = new CountingTask(prices, mid, to, threshold, priceThreshold, taskCounter);
        left.fork(); // левую пачку — в очередь (возможно, украдёт другой воркер)
        long rightCount = right.compute(); // правую считаем в текущем потоке
        return rightCount + left.join();   // складываем после завершения левой
    }

    private long countLeaf() {
        long found = 0;
        for (int i = from; i < to; i++) {
            if (prices[i] > priceThreshold) {
                found++;
            }
        }
        System.out.println("[" + Thread.currentThread().getName() + "] пачка [" + from + ".." + to
                + "): найдено " + found);
        return found;
    }
}