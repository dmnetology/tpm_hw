package com.example.demo;

import java.util.concurrent.RecursiveTask;

/**
 * Листовая задача-пачка для варианта «каждая пачка — новая задача»:
 * считает количество товаров дороже порога в одном непересекающемся
 * диапазоне без дальнейшего разбиения.
 */
public class BatchCountTask extends RecursiveTask<Long> {

    private final double[] prices;
    private final int from; // включительно
    private final int to;   // не включительно
    private final double priceThreshold;

    public BatchCountTask(double[] prices, int from, int to, double priceThreshold) {
        this.prices = prices;
        this.from = from;
        this.to = to;
        this.priceThreshold = priceThreshold;
    }

    @Override
    protected Long compute() {
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