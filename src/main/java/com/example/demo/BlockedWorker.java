package com.example.demo;

/**
 * Задача потока: демонстрирует состояние BLOCKED — пытается войти
 * в synchronized-блок, монитор которого удерживает другой поток (LockHolder),
 * получает монитор после его освобождения и завершается.
 */
public class BlockedWorker implements Runnable {

    private final Object lock;

    public BlockedWorker(Object lock) {
        this.lock = lock;
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        try {
            System.out.println(name + ": начал работу до критической секции");
            Thread.sleep(300); // TIMED_WAITING до попытки входа в synchronized
            System.out.println(name + ": пытаюсь войти в критическую секцию");
            synchronized (lock) { // монитор занят LockHolder — здесь поток в состоянии BLOCKED
                System.out.println(name + ": вошёл в критическую секцию");
                Thread.sleep(200); // TIMED_WAITING, монитор ещё удерживается
            }
            System.out.println(name + ": вышел из критической секции, работа завершена");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(name + ": поток прерван");
        }
    }
}