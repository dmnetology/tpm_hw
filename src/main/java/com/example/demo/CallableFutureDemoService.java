package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации Callable и Future. Этапы: отмена долгой
 * задачи через Future.cancel(), FutureTask как мост Callable → Runnable
 * (запуск через new Thread), пакетный запуск задач через invokeAll(),
 * периодическая задача в ScheduledExecutorService и окно @Scheduled
 * для PeriodicDataAggregator.
 */
@Service
public class CallableFutureDemoService {

    private static final long CANCEL_AFTER_MS = 3000;
    private static final int BULK_TASKS = 5;
    private static final long STATUS_PERIOD_MS = 1000;
    private static final int STATUS_RUNS = 5;

    private final PeriodicDataAggregator aggregator;

    public CallableFutureDemoService(PeriodicDataAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /** Демонстрация Callable и Future, вызывается при старте приложения. */
    public void runCallableFutureDemo() {
        try {
            runCancelDemo();
            runFutureTaskDemo();
            runInvokeAllDemo();
            runScheduledExecutorDemo();
            aggregator.runAggregationWindow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Демонстрация прервана");
        }
    }

    /** Выводит результат Future; сюда попадают только не брошенные задачами исключения. */
    private static Integer getQuietly(Future<Integer> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            return null;
        }
    }

    /**
     * Этап 1. Пул с одной долгой задачей (10 с имитации): через 3 секунды
     * cancel(true) прерывает её поток; после отмены isCancelled = true,
     * а get() бросает CancellationException.
     */
    private void runCancelDemo() throws InterruptedException {
        System.out.println("=== Callable и Future: долгая задача и отмена cancel() ===");
        ExecutorService executor = newFixedPool(1, "Worker");
        try {
            Future<String> future = executor.submit(new LongRunningTask());
            System.out.println("Задача отправлена в пул, Future получен; отмена будет через " + CANCEL_AFTER_MS / 1000 + " с");
            Thread.sleep(CANCEL_AFTER_MS);
            boolean cancelled = future.cancel(true); // true — прервать поток, если задача выполняется
            System.out.println("cancel(true) вернул " + cancelled + " — поток задачи прерван");
            System.out.println("isCancelled = " + future.isCancelled() + ", isDone = " + future.isDone());
            try {
                future.get();
            } catch (CancellationException e) {
                System.out.println("future.get() после отмены бросает " + e.getClass().getSimpleName());
            } catch (ExecutionException e) {
                // недостижимо после отмены, но по объявлению get() обязан быть обработан
            }
        } finally {
            shutdownQuietly(executor);
        }
    }

    /**
     * Этап 2. FutureTask — мост между Callable и Runnable: Callable нельзя
     * передать в new Thread напрямую, а FutureTask можно. Именно это
     * ExecutorService делает под капотом в submit().
     */
    private void runFutureTaskDemo() throws InterruptedException {
        System.out.println("=== FutureTask: мост Callable — Runnable, запуск через new Thread ===");
        Callable<Integer> callable = () -> {
            Thread.sleep(500);
            return 42;
        };
        FutureTask<Integer> futureTask = new FutureTask<>(callable);
        Thread thread = new Thread(futureTask, "FutureTask-Thread");
        thread.start();
        System.out.println("Callable запущен в new Thread через FutureTask, ждём результат...");
        thread.join();
        try {
            System.out.println("FutureTask вернул результат: " + futureTask.get()
                    + ", isDone = " + futureTask.isDone());
        } catch (ExecutionException e) {
            System.out.println("Задача упала: " + e.getCause());
        }
    }

    /**
     * Этап 3. invokeAll(): пакетный запуск задач, каждая работает случайные
     * 1–3 секунды и возвращает число; метод блокируется до завершения всех задач.
     */
    private void runInvokeAllDemo() throws InterruptedException {
        System.out.println("=== invokeAll: пакетный запуск " + BULK_TASKS + " задач ===");
        ExecutorService executor = newFixedPool(BULK_TASKS, "worker");
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 1; i <= BULK_TASKS; i++) {
                tasks.add(new RandomResultTask(i));
            }
            long start = System.nanoTime();
            // блокирует вызывающий поток, пока не завершатся все задачи пакета
            List<Future<Integer>> futures = executor.invokeAll(tasks);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            StringBuilder results = new StringBuilder();
            for (int i = 0; i < futures.size(); i++) {
                Integer value = getQuietly(futures.get(i));
                results.append(value).append(' ');
            }
            System.out.println("invokeAll вернул " + futures.size()
                    + " Future в порядке задач, все уже завершены (isDone=true)");
            System.out.println("Результаты: " + results.toString().trim());
            System.out.println("Пакет занял " + elapsed + " мс — времени самого долгого члена группы");
        } finally {
            shutdownQuietly(executor);
        }
    }

    /**
     * Этап 4. ScheduledExecutorService: периодическая задача «проверка статуса
     * системы» каждые 1 с (scheduleAtFixedRate), через 5 с отменяется
     * через ScheduledFuture.cancel().
     */
    private void runScheduledExecutorDemo() throws InterruptedException {
        System.out.println("=== ScheduledExecutorService: периодическая задача «проверка статуса системы» ===");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1,
                task -> new Thread(task, "Scheduler"));
        AtomicInteger runs = new AtomicInteger();
        ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(() -> {
            int n = runs.incrementAndGet();
            Runtime runtime = Runtime.getRuntime();
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            System.out.println("[статус #" + n + "] (" + Thread.currentThread().getName()
                    + "): занято памяти " + usedMb + " МБ, всего потоков " + Thread.activeCount());
        }, 0, STATUS_PERIOD_MS, TimeUnit.MILLISECONDS);
        // первый запуск происходит сразу (t=0), успеют запуски №1..№5, шестой — нет
        Thread.sleep(STATUS_RUNS * STATUS_PERIOD_MS - 200);
        handle.cancel(false); // отменяется расписание, текущее выполнение не прерывается
        scheduler.shutdown();
        System.out.println("Проверка статуса выполнялась раз в " + STATUS_PERIOD_MS + " мс, "
                + runs.get() + " запусков, затем отменена через ScheduledFuture.cancel()");
    }

    /** Пул с именованными потоками worker-N. */
    private static ExecutorService newFixedPool(int threads, String name) {
        AtomicInteger seq = new AtomicInteger();
        return Executors.newFixedThreadPool(threads,
                task -> new Thread(task, name + "-" + seq.incrementAndGet()));
    }

    private static void shutdownQuietly(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}