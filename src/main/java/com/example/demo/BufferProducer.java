package com.example.demo;

/**
 * Задача потока-производителя: кладёт в буфер заданное количество чисел.
 * Значения идут подряд от firstValue, чтобы потом сверить контрольную сумму.
 */
public class BufferProducer implements Runnable {

    private final Buffer<Integer> buffer;
    private final int totalItems;
    private final int firstValue;

    private int putCount;
    private long putSum;

    public BufferProducer(Buffer<Integer> buffer, int totalItems, int firstValue) {
        this.buffer = buffer;
        this.totalItems = totalItems;
        this.firstValue = firstValue;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < totalItems; i++) {
                int value = firstValue + i;
                buffer.put(value);
                putCount++;
                putSum += value;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить статус прерывания
        }
    }

    public int getPutCount() {
        return putCount;
    }

    public long getPutSum() {
        return putSum;
    }
}