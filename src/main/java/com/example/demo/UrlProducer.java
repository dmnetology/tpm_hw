package com.example.demo;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Производитель «сервера задач»: кладёт URL-задачи в ограниченную
 * ArrayBlockingQueue. put() блокируется, когда очередь полна, — так
 * обрабатывается переполнение. Темп имитирует реальную нагрузку: первые
 * задачи идут почти без пауз (пачка — очередь заполняется и переполняется),
 * затем producer «остывает» и очередь успевает опустошаться.
 */
public class UrlProducer implements Runnable {

    private static final int BURST_TASKS = 3;
    private static final long BURST_PAUSE_MS = 50;
    private static final long SLOW_PAUSE_MS = 2500;

    private final String name;
    private final BlockingQueue<UrlTask> queue;
    private final List<String> urls;

    public UrlProducer(String name, BlockingQueue<UrlTask> queue, List<String> urls) {
        this.name = name;
        this.queue = queue;
        this.urls = urls;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < urls.size(); i++) {
                // BlockingQueue не принимает null, а переполнение видит remainingCapacity()
                if (queue.remainingCapacity() == 0) {
                    System.out.println("[" + name + "] очередь полна — put() ждёт места");
                }
                queue.put(new UrlTask(urls.get(i))); // ждёт, если очередь заполнена
                System.out.println("[" + name + "] задача поставлена: " + urls.get(i)
                        + " (в очереди " + queue.size() + ")");
                // пауза только между задачами: после последней производитель сразу завершается
                if (i < urls.size() - 1) {
                    if (i < BURST_TASKS) {
                        Thread.sleep(BURST_PAUSE_MS);
                    } else {
                        Thread.sleep(SLOW_PAUSE_MS + ThreadLocalRandom.current().nextLong(300));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}