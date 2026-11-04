package com.example.demo;

/**
 * Задача потока-потребителя: извлекает числа из буфера, пока те появляются.
 * Завершается, когда подача закончена, а буфер пуст (take() вернул null).
 */
public class BufferConsumer implements Runnable {

    private final Buffer<Integer> buffer;

    private int takenCount;
    private long takenSum;

    public BufferConsumer(Buffer<Integer> buffer) {
        this.buffer = buffer;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Integer value = buffer.take();
                if (value == null) {
                    return; // подача закончена и буфер пуст — поток завершает работу
                }
                takenCount++;
                takenSum += value;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить статус прерывания
        }
    }

    public int getTakenCount() {
        return takenCount;
    }

    public long getTakenSum() {
        return takenSum;
    }
}