package com.example.demo;

/**
 * Класс задачи — вариант создания потока через Runnable.
 * Поток выводит своё имя и порядковый номер в цикле.
 */
public class CounterWorker implements Runnable {

    private static final int MAX_NUMBER = 5;

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        for (int i = 1; i <= MAX_NUMBER; i++) {
            System.out.println(name + ": порядковый номер " + i);
            try {
                Thread.sleep(100); // пауза, чтобы работа двух потоков была видна на экране
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(name + ": поток прерван");
                return;
            }
        }
    }
}