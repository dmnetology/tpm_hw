package com.example.demo;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Простой класс сервиса — создаёт и запускает потоки двумя способами
 * (Runnable и наследование Thread) и выводит список всех активных потоков.
 */
@Service
public class ThreadDemoService {

    /** Демонстрация создания и запуска потоков, вызывается при старте приложения. */
    public void runThreadDemo() {
        System.out.println("=== Демонстрация создания и запуска потоков ===");

        // Поток №1: унаследован от Thread, имя задано в конструкторе
        LoggerThread loggerThread = new LoggerThread();

        // Поток №2: создан через Runnable, имя передаётся в конструктор Thread
        Thread counterThread = new Thread(new CounterWorker(), "CounterWorker");

        loggerThread.start();
        counterThread.start();

        // Список всех активных потоков JVM (включая системные и потоки Tomcat)
        List<Thread> activeThreads = Thread.getAllStackTraces().keySet().stream()
                .sorted(Comparator.comparing(Thread::getName))
                .toList();
        System.out.println("=== Список всех активных потоков (всего " + activeThreads.size() + ") ===");
        for (int i = 0; i < activeThreads.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + activeThreads.get(i).getName());
        }

        // Ожидаем завершения обоих потоков
        try {
            loggerThread.join();
            counterThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("main: ожидание завершения потоков прервано");
        }

        System.out.println("=== Оба потока завершили работу ===");
    }
}