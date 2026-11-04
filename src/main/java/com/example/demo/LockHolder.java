package com.example.demo;

/**
 * Задача потока: захватывает монитор общего замка и не отпускает его во время сна
 * (sleep не снимает монитор) — этим создаётся состояние BLOCKED у конкурирующего потока.
 * Сам проходит через TIMED_WAITING. В конце пробуждает ожидающие потоки (notifyAll).
 */
public class LockHolder implements Runnable {

    private final Object lock;

    public LockHolder(Object lock) {
        this.lock = lock;
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        try {
            // фаза работы в RUNNABLE (без сна), чтобы наблюдатель зафиксировал состояние
            long until = System.nanoTime() + 300_000_000L; // 300 мс
            System.out.println(name + ": начал работу");
            while (System.nanoTime() < until) {
                Thread.onSpinWait();
            }
            System.out.println(name + ": захватываю монитор общего замка и засыпаю");
            synchronized (lock) {
                Thread.sleep(1500); // TIMED_WAITING, при этом монитор удерживается
                lock.notifyAll();   // пробуждение ожидающих потоков
            }
            System.out.println(name + ": работа завершена");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(name + ": поток прерван");
        }
    }
}