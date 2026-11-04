package com.example.demo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации volatile и atomic-переменных. Четыре этапа:
 * остановка потока через volatile-флаг, счётчик на AtomicInteger, singleton-кэш
 * на AtomicReference и сравнение производительности AtomicInteger,
 * volatile int и обычного int.
 */
@Service
public class VolatileAtomicsDemoService {

    private static final int FLAG_WORK_MS = 200;
    private static final int GETTER_THREADS = 8;
    private static final int INCREMENTS_PER_THREAD = 100_000;
    private static final int CACHE_THREADS = 8;
    private static final int CALLS_PER_THREAD = 100;
    private static final int RUNS = 3;

    /** Демонстрация volatile и atomic-переменных, вызывается при старте приложения. */
    public void runVolatileAtomicsDemo() {
        try {
            runFlagDemo();
            runCounterDemo();
            runCacheDemo();
            compareCounterVariants();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Демонстрация прервана");
        }
    }

    /**
     * Этап 1. Остановка потока: рабочий поток крутится в цикле, основной поток
     * устанавливает volatile-флаг — рабочий поток видит его и завершается.
     */
    private void runFlagDemo() throws InterruptedException {
        System.out.println("=== Остановка потока через volatile-флаг ===");
        FlagWorker worker = new FlagWorker();
        Thread thread = new Thread(worker, "Поток-флаг");
        thread.start();
        Thread.sleep(FLAG_WORK_MS); // рабочий поток успевает отработать в цикле
        System.out.println("Основной поток: устанавливаю volatile-флаг остановки");
        worker.requestStop();
        thread.join();
        System.out.println("Рабочий поток увидел флаг и вышел из цикла, выполнено итераций: "
                + worker.getIterations());
    }

    /** Этап 2. Счётчик на AtomicInteger под конкуренцией потоков. */
    private void runCounterDemo() throws InterruptedException {
        System.out.println("=== AtomicInteger: потокобезопасный счётчик ===");
        System.out.println("Потоков: " + GETTER_THREADS + ", инкрементов на поток: " + INCREMENTS_PER_THREAD);
        LoadResult result = runCounterLoad(new AtomicCounter());
        int expected = GETTER_THREADS * INCREMENTS_PER_THREAD;
        System.out.println("Итоговое значение: " + result.value() + " из " + expected
                + " — все инкременты учтены: " + (result.value() == expected));
    }

    /**
     * Этап 3. Singleton-кэш на AtomicReference: несколько потоков обращаются
     * к кэшу, значение создаётся ровно один раз.
     */
    private void runCacheDemo() throws InterruptedException {
        System.out.println("=== AtomicReference: singleton-кэш ===");
        System.out.println("Потоков: " + CACHE_THREADS + ", обращений на поток: " + CALLS_PER_THREAD);
        SingletonCache cache = new SingletonCache();
        List<CacheGetter> getters = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= CACHE_THREADS; i++) {
            CacheGetter getter = new CacheGetter(cache, CALLS_PER_THREAD);
            getters.add(getter);
            Thread thread = new Thread(getter, "Читатель-" + i);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        Set<String> distinct = new HashSet<>();
        int total = 0;
        for (CacheGetter getter : getters) {
            distinct.addAll(getter.getResults());
            total += getter.getResults().size();
        }
        System.out.println("Обращений: " + total + ", уникальных значений: " + distinct.size()
                + " — все потоки получили одно значение: " + (distinct.size() == 1));
    }

    /**
     * Этап 4. Сравнение производительности и корректности трёх вариантов
     * счётчика: AtomicInteger (атомарен), volatile int (видим, но не атомарен)
     * и обычный int.
     */
    private void compareCounterVariants() throws InterruptedException {
        System.out.println("=== Сравнение: AtomicInteger, volatile int и обычный int ===");
        System.out.println("Потоков: " + GETTER_THREADS + ", инкрементов на поток: " + INCREMENTS_PER_THREAD);

        // прогрев JIT: по одному незасчитанному прогону для каждого варианта
        runCounterLoad(new AtomicCounter());
        runCounterLoad(new VolatileIntCounter());
        runCounterLoad(new PlainIntCounter());

        long totalAtomic = 0;
        long totalVolatile = 0;
        long totalPlain = 0;
        LoadResult atomicResult = null;
        LoadResult volatileResult = null;
        LoadResult plainResult = null;
        for (int i = 1; i <= RUNS; i++) {
            atomicResult = runCounterLoad(new AtomicCounter());
            totalAtomic += atomicResult.nanos();
            volatileResult = runCounterLoad(new VolatileIntCounter());
            totalVolatile += volatileResult.nanos();
            plainResult = runCounterLoad(new PlainIntCounter());
            totalPlain += plainResult.nanos();
            System.out.printf("Запуск %d: AtomicInteger %.1f мс, volatile %.1f мс, обычный int %.1f мс%n",
                    i, atomicResult.nanos() / 1e6, volatileResult.nanos() / 1e6, plainResult.nanos() / 1e6);
        }
        System.out.printf("Среднее: AtomicInteger %.1f мс, volatile %.1f мс, обычный int %.1f мс%n",
                totalAtomic / (double) RUNS / 1e6, totalVolatile / (double) RUNS / 1e6,
                totalPlain / (double) RUNS / 1e6);

        int expected = GETTER_THREADS * INCREMENTS_PER_THREAD;
        System.out.println("Итог AtomicInteger: " + atomicResult.value() + " из " + expected
                + " — верно: " + (atomicResult.value() == expected));
        System.out.println("Итог volatile int: " + volatileResult.value() + " — потеряно "
                + (expected - volatileResult.value()) + " инкрементов (видимость есть, атомарности нет)");
        System.out.println("Итог обычного int: " + plainResult.value() + " — потеряно "
                + (expected - plainResult.value()) + " инкрементов");
    }

    private record LoadResult(long nanos, long value) {
    }

    /** Один нагрузочный прогон счётчика: N потоков крутят инкременты. */
    private LoadResult runCounterLoad(Counter counter) throws InterruptedException {
        Thread[] threads = new Thread[GETTER_THREADS];
        for (int i = 0; i < GETTER_THREADS; i++) {
            threads[i] = new Thread(() -> {
                for (int op = 0; op < INCREMENTS_PER_THREAD; op++) {
                    counter.increment();
                }
            }, "Инкремент-" + (i + 1));
        }
        long start = System.nanoTime();
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        return new LoadResult(System.nanoTime() - start, counter.get());
    }

    /** Счётчик с полем volatile: значение видно всем потокам, но инкремент не атомарен. */
    private static final class VolatileIntCounter implements Counter {
        private volatile int value;

        @Override
        public void increment() {
            value++; // read -> inc -> write, между шагами может вклиниться другой поток
        }

        @Override
        public long get() {
            return value;
        }
    }

    /** Счётчик без volatile и без атомарности — для сравнения. */
    private static final class PlainIntCounter implements Counter {
        private int value;

        @Override
        public void increment() {
            value++;
        }

        @Override
        public long get() {
            return value;
        }
    }
}