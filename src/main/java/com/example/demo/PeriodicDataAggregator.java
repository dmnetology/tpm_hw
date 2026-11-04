package com.example.demo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Периодический агрегатор (часть 2 задания): каждые N секунд получает список
 * идентификаторов сущностей — валют — и для каждой валюты асинхронно
 * запрашивает актуальный курс через Callable в пуле потоков. Внешний источник
 * имитируется методом fetchRate(), который может выбросить IOException:
 * исключение перехватывается пулом и прилетает обёрнутым
 * в ExecutionException при Future.get(). Успешные результаты логируются
 * и сохраняются в таблицу последних курсов.
 */
@Service
public class PeriodicDataAggregator {

    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "JPY", "CNY");
    private static final Map<String, Double> BASE_RATES = Map.of(
            "USD", 90.0, "EUR", 100.0, "GBP", 115.0, "JPY", 0.65, "CNY", 12.5);
    private static final long PERIOD_MS = 2000; // N секунд из условия задания
    private static final int TOTAL_TICKS = 5;
    private static final int FAILURE_CHANCE_PERCENT = 20;

    private final AtomicInteger fetchSeq = new AtomicInteger();
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(CURRENCIES.size(),
            task -> {
                Thread thread = new Thread(task, "fetch-" + fetchSeq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

    // все мутируемые поля живут только в потоке планировщика Spring — обычных int достаточно
    private int tick;
    private int successes;
    private int failures;
    private final Map<String, Double> lastRates = new HashMap<>();

    /** Окно демонстрации включено runner'ом: приложение живёт постоянно, тики — только внутри окна. */
    private volatile boolean running;
    private volatile CountDownLatch windowDone;

    /** Тик агрегатора по расписанию Spring: вне окна демонстрации неактивен. */
    @Scheduled(fixedRate = PERIOD_MS)
    public void aggregate() {
        if (!running) {
            return;
        }
        tick++;
        System.out.println("--- @Scheduled тик " + tick + "/" + TOTAL_TICKS
                + ": список из " + CURRENCIES.size() + " валют, запросы асинхронно ---");

        List<Future<Double>> futures = new ArrayList<>();
        for (String currency : CURRENCIES) {
            futures.add(fetchPool.submit(() -> fetchRate(currency)));
        }
        for (int i = 0; i < futures.size(); i++) {
            String currency = CURRENCIES.get(i);
            try {
                double rate = futures.get(i).get(); // ExecutionException при сбое источника
                successes++;
                lastRates.put(currency, rate);
                System.out.println("  [агрегатор] " + currency + " = " + rate + " — сохранён");
            } catch (ExecutionException e) {
                failures++;
                System.out.println("  [агрегатор] " + currency + " — сбой источника: "
                        + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (tick >= TOTAL_TICKS) {
            running = false;
            printSummary();
            CountDownLatch done = windowDone;
            if (done != null) {
                done.countDown();
            }
        }
    }

    /**
     * Окно демонстрации: включает периодические тики блокирующего runner'а
     * и ждёт, пока агрегатор отработает все тики и выведет сводку.
     */
    public void runAggregationWindow() throws InterruptedException {
        System.out.println("=== @Scheduled: PeriodicDataAggregator, тики каждые "
                + PERIOD_MS / 1000 + " с ===");
        tick = 0;
        successes = 0;
        failures = 0;
        lastRates.clear();
        CountDownLatch latch = new CountDownLatch(1);
        windowDone = latch;
        running = true;
        latch.await(90, TimeUnit.SECONDS);
    }

    /** Имитация запроса ко внешнему источнику: время ответа, случайные сбои, курс. */
    private double fetchRate(String currency) throws IOException, InterruptedException {
        long latencyMs = ThreadLocalRandom.current().nextLong(150, 700);
        Thread.sleep(latencyMs);
        System.out.println("  [" + Thread.currentThread().getName() + " → " + currency
                + "] ответ за " + latencyMs + " мс");
        if (ThreadLocalRandom.current().nextInt(100) < FAILURE_CHANCE_PERCENT) {
            throw new IOException("источник \"" + currency + "\" недоступен");
        }
        double rate = BASE_RATES.get(currency) * (1 + ThreadLocalRandom.current().nextDouble(-0.02, 0.02));
        return Math.round(rate * 100) / 100.0;
    }

    private void printSummary() {
        System.out.println("--- @Scheduled: окно демонстрации завершено ---");
        System.out.println("Итого за " + TOTAL_TICKS + " тиков (~" + TOTAL_TICKS * PERIOD_MS / 1000
                + " с): запросов к данным " + (successes + failures)
                + ", успешных " + successes + ", сбоев " + failures);
        System.out.println("Последние сохранённые курсы: " + new TreeMap<>(lastRates));
    }
}