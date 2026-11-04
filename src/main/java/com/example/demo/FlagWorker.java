package com.example.demo;

/**
 * Задача потока, останавливаемая через volatile-флаг: крутится в рабочем
 * цикле, проверяя флаг на каждой итерации. volatile даёт видимость — как
 * только другой поток установит флаг, рабочий поток увидит true и выйдет
 * из цикла. Сам флаг volatile делает только видимым, атомарности он не даёт.
 */
public class FlagWorker implements Runnable {

    /** Флаг завершения — volatile, читается из чужого потока. */
    private volatile boolean stopRequested;

    private long iterations;

    /** Просьба остановиться — вызывает другой поток. */
    public void requestStop() {
        stopRequested = true;
    }

    @Override
    public void run() {
        while (!stopRequested) {
            iterations++;
            Thread.onSpinWait(); // имитация полезной работы в цикле
        }
    }

    public long getIterations() {
        return iterations;
    }
}