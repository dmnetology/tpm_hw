package com.example.demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации многопоточных коллекций. Этапы: потерянные
 * обновления обычной HashMap против атомарного merge в ConcurrentHashMap,
 * затем сама система событий: производители публикуют события
 * в ConcurrentLinkedQueue, потребитель рассылает их слушателям из
 * CopyOnWriteArrayList (с подпиской нового слушателя в середине прогона),
 * статистика по типам ведётся в ConcurrentHashMap.
 */
@Service
public class EventSystemDemoService {

    private static final int INCREMENT_THREADS = 10;
    private static final int INCREMENTS_PER_THREAD = 1000;
    private static final int PRODUCERS = 3;
    private static final int EVENTS_PER_PRODUCER = 10;

    private static final List<String> EVENT_TYPES =
            List.of("LOGIN", "VIEW", "CLICK", "PURCHASE", "LOGOUT");

    /** Демонстрация многопоточных коллекций, вызывается при старте приложения. */
    public void runEventSystemDemo() {
        try {
            runUnsafeMapStage();
            runConcurrentMapStage();
            runEventSystemStage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Демонстрация прервана");
        }
    }

    /** Этап 1. Обычная HashMap: пара get/put неатомарна — обновления теряются. */
    private void runUnsafeMapStage() throws InterruptedException {
        System.out.println("=== Проблема обычной HashMap: потерянные обновления ===");
        Map<String, Integer> unsafe = new HashMap<>();
        unsafe.put("counter", 0);
        long elapsed = runIncrementStage(unsafe, false);
        int actual = unsafe.get("counter");
        System.out.println("Обычная HashMap: " + INCREMENT_THREADS + " потоков по " + INCREMENTS_PER_THREAD
                + " инкрементов за " + elapsed + " мс: ожидалось " + INCREMENT_THREADS * INCREMENTS_PER_THREAD
                + ", фактически " + actual + " — потеряно " + (INCREMENT_THREADS * INCREMENTS_PER_THREAD - actual));
    }

    /** Этап 2. ConcurrentHashMap с атомарным merge: потери исчезают. */
    private void runConcurrentMapStage() throws InterruptedException {
        System.out.println("=== ConcurrentHashMap: атомарный merge ===");
        Map<String, Integer> safe = new ConcurrentHashMap<>();
        safe.put("counter", 0);
        long elapsed = runIncrementStage(safe, true);
        int actual = safe.get("counter");
        System.out.println("ConcurrentHashMap: те же " + INCREMENT_THREADS + " потоков по " + INCREMENTS_PER_THREAD
                + " инкрементов за " + elapsed + " мс: ожидалось " + INCREMENT_THREADS * INCREMENTS_PER_THREAD
                + ", фактически " + actual + " — потеряно " + (INCREMENT_THREADS * INCREMENTS_PER_THREAD - actual));
    }

    /** Инкремент общего счётчика: неатомарная пара get/put против атомарного merge. */
    private long runIncrementStage(Map<String, Integer> map, boolean atomicMerge) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(INCREMENT_THREADS);
        long start = System.nanoTime();
        try {
            for (int t = 0; t < INCREMENT_THREADS; t++) {
                executor.submit(() -> {
                    for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
                        if (atomicMerge) {
                            map.merge("counter", 1, Integer::sum); // атомарная операция
                        } else {
                            Integer value = map.get("counter"); // get и put разделены — гонка
                            map.put("counter", value == null ? 1 : value + 1);
                        }
                    }
                });
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** Этап 3. Система событий: очередь, слушатели, статистика, подписка на лету. */
    private void runEventSystemStage() throws InterruptedException {
        System.out.println("=== Система событий пользователей веб-приложения ===");
        EventSystemService system = new EventSystemService();
        system.subscribe(new LoggingListener());
        system.subscribe(new StatsListener(system.getCountsByType()));
        System.out.println("Подписаны слушатели: Логгер, Счётчик");

        Thread consumerThread = new Thread(system::handleEvents, "event-consumer");
        consumerThread.start();

        AtomicInteger producerSeq = new AtomicInteger();
        ExecutorService producers = Executors.newFixedThreadPool(PRODUCERS,
                task -> new Thread(task, "event-producer-" + producerSeq.incrementAndGet()));
        try {
            for (int t = 0; t < PRODUCERS; t++) {
                producers.submit(() -> {
                    for (int i = 0; i < EVENTS_PER_PRODUCER; i++) {
                        UserEvent event = new UserEvent(
                                "user-" + (1 + ThreadLocalRandom.current().nextInt(5)),
                                EVENT_TYPES.get(ThreadLocalRandom.current().nextInt(EVENT_TYPES.size())));
                        system.publish(event); // неблокирующий offer
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 31));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                });
            }
        } finally {
            // подписка аудиторского слушателя в середину прогона, пока производители
            // ещё публикуют: CopyOnWriteArrayList позволяет менять список, когда
            // потребитель по нему итерируется
            awaitProcessed(system, 15);
            system.subscribe(new AuditListener());
            System.out.println("[система] слушатель Аудит добавлен посреди прогона");
            producers.shutdown();
            producers.awaitTermination(30, TimeUnit.SECONDS);
        }

        system.finishProduction();
        consumerThread.join();

        System.out.println("Обработано событий: " + system.getProcessedCount()
                + " (производители: " + PRODUCERS + " потока по " + EVENTS_PER_PRODUCER + " событий)");
        System.out.println("Счётчики по типам: " + new TreeMap<>(system.getCountsByType()));
    }

    /** Ждёт, пока потребитель обработает не меньше target событий (с защитой от зависания). */
    private static void awaitProcessed(EventSystemService system, int target) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (system.getProcessedCount() < target && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }
}