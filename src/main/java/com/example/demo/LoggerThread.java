package com.example.demo;

/**
 * Класс задачи — вариант создания потока через наследование от Thread.
 * Поток выводит своё имя и порядковый номер в цикле.
 */
public class LoggerThread extends Thread {

    private static final int MAX_NUMBER = 5;

    public LoggerThread() {
        // понятное имя потока
        super("LoggerThread");
    }

    @Override
    public void run() {
        for (int i = 1; i <= MAX_NUMBER; i++) {
            System.out.println(getName() + ": порядковый номер " + i);
            try {
                Thread.sleep(100); // пауза, чтобы работа двух потоков была видна на экране
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(getName() + ": поток прерван");
                return;
            }
        }
    }
}