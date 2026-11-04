package com.example.demo;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Задача для пакетного запуска через invokeAll: имитирует работу случайное
 * время от 1 до 3 секунд и возвращает результат — случайное число.
 */
public class RandomResultTask implements Callable<Integer> {

    private final int number;

    public RandomResultTask(int number) {
        this.number = number;
    }

    @Override
    public Integer call() throws Exception {
        long workMs = ThreadLocalRandom.current().nextLong(1000, 3001);
        Thread.sleep(workMs);
        int result = ThreadLocalRandom.current().nextInt(10, 100);
        System.out.println("[" + taskName() + "] готова за " + workMs + " мс, результат: " + result);
        return result;
    }

    private String taskName() {
        return "Задача-" + number + " (" + Thread.currentThread().getName() + ")";
    }
}