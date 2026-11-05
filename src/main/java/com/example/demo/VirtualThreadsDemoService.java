package com.example.demo;

import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Демонстрация задания 14: 10 000 задач на виртуальных и платформенных потоках,
 * сравнение времени и ресурсов, StructuredTaskScope для управления группой
 * виртуальных потоков с обработкой сбоя и отмены.
 */
@Service
public class VirtualThreadsDemoService {

    /** Число задач в основном прогоне (по заданию). */
    private static final int MAIN_TASKS = 10_000;
    /** Размеры тестовых прогонов пункта 6 (без печати имён). */
    private static final int[] SWEEP_SIZES = {1_000, 10_000, 50_000};
    /** Число подзадач в демонстрации StructuredTaskScope. */
    private static final int SCOPE_TASKS = 100;

    public void runVirtualThreadsDemo() throws InterruptedException {
        System.out.println("=== Задание 14. Виртуальные потоки (Project Loom) ===");

        System.out.println("=== Этап 1. Подготовка задач ===");
        System.out.println(fmt(MAIN_TASKS) + " задач: каждая печатает имя своего потока, затем "
                + IoEmulationTask.IO_PAUSE_MS + " мс sleep() — имитация неблокирующего I/O");

        System.out.println("=== Этап 2. Виртуальные потоки: Thread.ofVirtual().start() ===");
        long[] virtual = runVirtualBatch(MAIN_TASKS, true);

        System.out.println("=== Этап 3. Платформенные потоки: new Thread().start() ===");
        long[] platform = runPlatformBatch(MAIN_TASKS, true);

        System.out.println("=== Этап 4. Сравнение на " + fmt(MAIN_TASKS) + " задачах ===");
        System.out.println("виртуальные:   создание и запуск " + fmt(virtual[0]) + " мс, полное выполнение "
                + fmt(virtual[1]) + " мс");
        System.out.println("платформенные: создание и запуск " + fmt(platform[0]) + " мс, полное выполнение "
                + fmt(platform[1]) + " мс");
        System.out.println("создание — быстрее в " + decimalRu((double) platform[0] / virtual[0])
                + " раза, полное выполнение — в " + decimalRu((double) platform[1] / virtual[1]) + " раза");

        runStructuredTaskScope();
        runSweep();
    }

    /** Этап 2 и часть 6: пакет задач на виртуальных потоках; возвращает {создание мс, полное мс}. */
    private long[] runVirtualBatch(int taskCount, boolean verbose) throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        long start = System.nanoTime();
        long cpuStart = cpuTime();
        Thread[] threads = new Thread[taskCount];
        Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("vt-", 1);
        long liveMid = 0;
        long heapMidMb = 0;
        for (int i = 0; i < taskCount; i++) {
            threads[i] = builder.start(new IoEmulationTask(i + 1, verbose, completed));
            if (i == taskCount / 2) {
                liveMid = threadCount();
                heapMidMb = heapUsedMb();
            }
        }
        long createMs = elapsedMs(start);
        for (Thread thread : threads) {
            thread.join();
        }
        long totalMs = elapsedMs(start);
        long cpuMs = (cpuTime() - cpuStart) / 1_000_000;
        if (verbose) {
            printBatchSummary("виртуальные", taskCount, createMs, totalMs, liveMid, heapMidMb, cpuMs, threadCount());
        }
        return new long[] {createMs, totalMs};
    }

    /** Этап 3 и часть 6: пакет задач на платформенных потоках. */
    private long[] runPlatformBatch(int taskCount, boolean verbose) throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        long start = System.nanoTime();
        long cpuStart = cpuTime();
        Thread[] threads = new Thread[taskCount];
        long liveMid = 0;
        long heapMidMb = 0;
        for (int i = 0; i < taskCount; i++) {
            threads[i] = new Thread(new IoEmulationTask(i + 1, verbose, completed), "pt-" + (i + 1));
            threads[i].start();
            if (i == taskCount / 2) {
                liveMid = threadCount();
                heapMidMb = heapUsedMb();
            }
        }
        long createMs = elapsedMs(start);
        for (Thread thread : threads) {
            thread.join();
        }
        long totalMs = elapsedMs(start);
        long cpuMs = (cpuTime() - cpuStart) / 1_000_000;
        if (verbose) {
            printBatchSummary("платформенные", taskCount, createMs, totalMs, liveMid, heapMidMb, cpuMs, threadCount());
        }
        return new long[] {createMs, totalMs};
    }

    /** Сводка по прогону: времена, число живых потоков JVM (виртуальные в ThreadMXBean не отражаются), память. */
    private void printBatchSummary(String label, int taskCount, long createMs, long totalMs,
                                   long liveMid, long heapMidMb, long cpuMs, long liveAfterDone) {
        System.out.println("[" + label + "] " + fmt(taskCount) + " задач: создание и запуск "
                + fmt(createMs) + " мс");
        System.out.println("[" + label + "] в разгар запуска: потоков в JVM " + fmt(liveMid)
                + ", память heap " + heapMidMb + " МБ");
        System.out.println("[" + label + "] все задачи завершены за " + fmt(totalMs) + " мс");
        System.out.println("[" + label + "] процессорное время JVM за прогон: " + fmt(cpuMs) + " мс");
        System.out.println("[" + label + "] после завершения в JVM потоков: " + fmt(liveAfterDone));
    }

    /** Этап 5: StructuredTaskScope — успешный прогон и прогон со сбоем. */
    private void runStructuredTaskScope() throws InterruptedException {
        System.out.println("=== Этап 5. StructuredTaskScope: группа из " + fmt(SCOPE_TASKS) + " виртуальных потоков ===");

        System.out.println("--- ожидание всех подзадач: fork() + join() ---");
        AtomicInteger completed = new AtomicInteger();
        List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();
        String firstResult = null;
        try (StructuredTaskScope<String> scope =
                     new StructuredTaskScope<>("demo-scope", Thread.ofVirtual().name("sts-", 1).factory())) {
            for (int i = 1; i <= SCOPE_TASKS; i++) {
                subtasks.add(scope.fork(new ScopeTask(i, false, true, completed)));
            }
            scope.join();
            firstResult = subtasks.get(0).get();
        }
        System.out.println("[scope] подзадач завершено: " + completed.get() + " из " + fmt(SCOPE_TASKS)
                + "; результат первой: " + firstResult);

        runScopeFailure();
    }

    /** Прогон со сбоем: одна подзадача упала — остальная группа отменена, причина из throwIfFailed(). */
    @SuppressWarnings("unchecked")
    private void runScopeFailure() throws InterruptedException {
        System.out.println("--- обработка исключений и отмены: ShutdownOnFailure ---");
        AtomicInteger completed = new AtomicInteger();
        List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();
        String failureCause = null;
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 1; i <= SCOPE_TASKS; i++) {
                subtasks.add(scope.fork(new ScopeTask(i, i == ScopeTask.FAILURE_ID, false, completed)));
            }
            try {
                scope.join().throwIfFailed();
            } catch (ExecutionException e) {
                failureCause = String.valueOf(e.getCause());
            }
        }
        Map<String, Integer> states = new HashMap<>();
        for (StructuredTaskScope.Subtask<?> subtask : subtasks) {
            states.merge(subtask.state().toString(), 1, Integer::sum);
        }
        System.out.println("[scope] сбойная подзадача упала, остальные отменены, причина из throwIfFailed(): "
                + failureCause);
        System.out.println("[scope] успешно выполнено: " + completed.get() + " из " + fmt(SCOPE_TASKS)
                + "; состояния после закрытия scope: " + states);
    }

    /** Этап 6: тестирование с разным числом задач (без печати имён, чтобы время было чистым). */
    private void runSweep() throws InterruptedException {
        System.out.println("=== Этап 6. Тестирование с разным числом задач (без печати имён) ===");
        List<String> virtualRows = new ArrayList<>();
        List<String> platformRows = new ArrayList<>();
        for (int size : SWEEP_SIZES) {
            long total = runVirtualBatch(size, false)[1];
            virtualRows.add(fmt(size) + " — " + fmt(total) + " мс");
        }
        for (int size : SWEEP_SIZES) {
            if (size > MAIN_TASKS) {
                continue;
            }
            long total = runPlatformBatch(size, false)[1];
            platformRows.add(fmt(size) + " — " + fmt(total) + " мс");
        }
        System.out.println("виртуальные:   " + String.join("; ", virtualRows));
        System.out.println("платформенные: " + String.join("; ", platformRows));
        System.out.println("виртуальные потоки свободно масштабируются дальше: платформенные ограничены памятью — "
                + "стек каждого ~1 МБ");
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Живые платформенные потоки JVM по ThreadMXBean (виртуальные в него не входят). */
    private long threadCount() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    /** Суммарное процессорное время процесса JVM c момента старта, нс. */
    private long cpuTime() {
        return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getProcessCpuTime();
    }

    private long heapUsedMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    /** Число с пробелами между разрядами: 10000 → "10 000". */
    private String fmt(long value) {
        String digits = Long.toString(value);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            sb.append(digits.charAt(i));
            int left = digits.length() - 1 - i;
            if (left > 0 && left % 3 == 0) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /** Дробное в 1 знак после запятой: 3.25 → "3,3". */
    private String decimalRu(double value) {
        long tenths = Math.round(value * 10);
        return (tenths / 10) + "," + (tenths % 10);
    }
}