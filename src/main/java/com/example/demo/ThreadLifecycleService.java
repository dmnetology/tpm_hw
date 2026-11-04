package com.example.demo;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации жизненного цикла потоков: создаёт три потока,
 * каждый из которых проходит через разные состояния (NEW, RUNNABLE, WAITING,
 * BLOCKED, TIMED_WAITING, TERMINATED), и поток-наблюдатель, который печатает
 * смену состояний в консоль.
 */
@Service
public class ThreadLifecycleService {

    /** Демонстрация состояний потока, вызывается при старте приложения. */
    public void runLifecycleDemo() {
        System.out.println("=== Демонстрация жизненного цикла потоков ===");

        // общий замок для демонстрации WAITING и BLOCKED
        Object lock = new Object();

        Thread lockHolder = new Thread(new LockHolder(lock), "LockHolder");
        Thread waitingWorker = new Thread(new WaitingWorker(lock), "WaitingWorker");
        Thread blockedWorker = new Thread(new BlockedWorker(lock), "BlockedWorker");

        // наблюдатель стартует первым — фиксирует и исходное состояние NEW
        Thread observer = new Thread(
                new ThreadStateObserver(List.of(lockHolder, waitingWorker, blockedWorker)),
                "Observer");
        try {
            observer.start();
            Thread.sleep(150); // наблюдатель успевает зафиксировать исходное состояние NEW всех потоков
            waitingWorker.start();    // первым входит в wait() — иначе не поймает уведомление
            Thread.sleep(300);        // ждём, пока WaitingWorker перейдёт в WAITING
            lockHolder.start();       // захватывает общий замок и удерживает его во сне
            blockedWorker.start();    // упрётся в занятый монитор — состояние BLOCKED

            lockHolder.join();
            waitingWorker.join();
            blockedWorker.join();
            observer.join(); // наблюдатель завершается, когда все три потока в TERMINATED
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("main: ожидание потоков прервано");
        }

        System.out.println("=== Демонстрация жизненного цикла завершена ===");
    }
}