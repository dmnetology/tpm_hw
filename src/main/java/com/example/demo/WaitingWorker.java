package com.example.demo;

/**
 * Задача потока: демонстрирует состояние WAITING — входит в synchronized-блок
 * и бесконечно ждёт уведомления (lock.wait()), после notifyAll() завершается.
 */
public class WaitingWorker implements Runnable {

    private final Object lock;

    public WaitingWorker(Object lock) {
        this.lock = lock;
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        try {
            System.out.println(name + ": начал работу");
            Thread.sleep(150); // TIMED_WAITING до входа в блок ожидания
            synchronized (lock) {
                System.out.println(name + ": перехожу в ожидание уведомления");
                lock.wait(); // WAITING до вызова notify()/notifyAll()
            }
            System.out.println(name + ": получил уведомление, завершаю работу");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(name + ": поток прерван");
        }
    }
}