package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации синхронизации потоков. Три этапа:
 * нагрузочный тест общего коллектора с wait()`/notifyAll(), сравнение
 * времени синхронизированных и несинхронизированных операций и перенос
 * элементов между двумя коллекторами без взаимной блокировки.
 */
@Service
public class SynchronizationDemoService {

    private static final int PRODUCER_THREADS = 4;
    private static final int CONSUMER_THREADS = 4;
    private static final int ITEMS_PER_PRODUCER = 250;
    private static final int DISTINCT_KEYS = 250; // все производители кладут одни и те же ключи
    private static final int WRITE_THREADS = 8;
    private static final int WRITES_PER_THREAD = 50_000;
    private static final int RUNS = 3;
    private static final int TRANSFER_ROUNDS = 3;
    private static final int TRANSFER_AMOUNT = 25;

    /** Демонстрация синхронизации потоков, вызывается при старте приложения. */
    public void runSynchronizationDemo() {
        try {
            runCollectorLoadTest();
            compareSynchronizedAndRaw();
            runDeadlockSafeTransfer();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Демонстрация прервана");
        }
    }

    /**
     * Этап 1. Нагрузочный тест: несколько потоков одновременно пишут в общий
     * коллектор и одновременно разбирают данные из него. Проверяется целостность
     * данных: счётчики совпадают, повторная работа не выполняется.
     */
    private void runCollectorLoadTest() throws InterruptedException {
        System.out.println("=== DataCollector: одновременный доступ, wait() и notifyAll() ===");
        DataCollector collector = new DataCollector("коллектор");

        List<ProducerWorker> producers = new ArrayList<>();
        List<ProcessingWorker> consumers = new ArrayList<>();
        List<Thread> producerThreads = new ArrayList<>();
        List<Thread> consumerThreads = new ArrayList<>();

        // обработчики стартуют первыми: список пуст, и они переходят в wait()
        for (int i = 1; i <= CONSUMER_THREADS; i++) {
            ProcessingWorker worker = new ProcessingWorker(collector);
            consumers.add(worker);
            Thread thread = new Thread(worker, "Обработчик-" + i);
            consumerThreads.add(thread);
            thread.start();
        }

        try {
            Thread.sleep(100); // обработчики успевают заснуть в wait() на пустом списке

            for (int i = 1; i <= PRODUCER_THREADS; i++) {
                ProducerWorker worker = new ProducerWorker(collector, ITEMS_PER_PRODUCER, DISTINCT_KEYS);
                producers.add(worker);
                Thread thread = new Thread(worker, "Сборщик-" + i);
                producerThreads.add(thread);
                thread.start();
            }
            for (Thread thread : producerThreads) {
                thread.join();
            }
            collector.finishProduction();
            for (Thread thread : consumerThreads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int collected = 0;
        int processed = 0;
        int skipped = 0;
        for (ProducerWorker producer : producers) {
            collected += producer.getCollected();
        }
        for (ProcessingWorker consumer : consumers) {
            processed += consumer.getProcessed();
            skipped += consumer.getSkippedDuplicates();
        }

        System.out.println("Собрано элементов: " + collected
                + " (ожидалось " + PRODUCER_THREADS * ITEMS_PER_PRODUCER + ")");
        System.out.println("Обработано: " + processed + ", пропущено повторов: " + skipped
                + " — нет дублирования работы: " + (processed == DISTINCT_KEYS));
        System.out.println("Счётчик коллектора: " + collector.getProcessedCount()
                + ", уникальных ключей: " + collector.getProcessedKeyCount()
                + " — совпадают: " + (collector.getProcessedCount() == collector.getProcessedKeyCount()));
        System.out.println("Список после обработки пуст: " + (collector.getPendingCount() == 0));
    }

    /**
     * Этап 2. Сравнение производительности: несколько потоков одновременно
     * пишут в общий список и увеличивают счётчик в синхронизированном и
     * несинхронизированном вариантах. Замеряется время и проверяется итог.
     */
    private void compareSynchronizedAndRaw() throws InterruptedException {
        System.out.println("=== Сравнение: synchronized и несинхронизированные операции ===");
        System.out.println("Потоков записи: " + WRITE_THREADS + ", записей на поток: " + WRITES_PER_THREAD);

        // прогрев JIT: по одному незасчитанному прогону для каждого варианта
        writeLoad(SynchronizedStore::new);
        writeLoad(UnsynchronizedStore::new);

        long totalSync = 0;
        long totalRaw = 0;
        WriteResult syncResult = null;
        WriteResult rawResult = null;
        for (int i = 1; i <= RUNS; i++) {
            syncResult = writeLoad(SynchronizedStore::new);
            totalSync += syncResult.nanos();
            rawResult = writeLoad(UnsynchronizedStore::new);
            totalRaw += rawResult.nanos();
            System.out.printf("Запуск %d: synchronized %.1f мс, без синхронизации %.1f мс%n",
                    i, syncResult.nanos() / 1e6, rawResult.nanos() / 1e6);
        }
        System.out.printf("Среднее: synchronized %.1f мс, без синхронизации %.1f мс%n",
                totalSync / (double) RUNS / 1e6, totalRaw / (double) RUNS / 1e6);

        int expected = WRITE_THREADS * WRITES_PER_THREAD;
        int lost = (int) Math.max(0, expected - rawResult.written());
        System.out.println("Итоговый счётчик synchronized: " + syncResult.written() + " из " + expected
                + " — верно: " + (syncResult.written() == expected));
        System.out.println("Итоговый счётчик без синхронизации: " + rawResult.written()
                + " — потеряно " + lost + " записей, исключений: " + rawResult.exceptions());
    }

    private WriteResult writeLoad(Supplier<Store> storeFactory) throws InterruptedException {
        Store store = storeFactory.get();
        AtomicInteger exceptions = new AtomicInteger();
        Thread[] threads = new Thread[WRITE_THREADS];
        for (int i = 0; i < WRITE_THREADS; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int op = 0; op < WRITES_PER_THREAD; op++) {
                        store.write(op);
                    }
                } catch (Exception e) {
                    exceptions.incrementAndGet(); // несинхронизированный список может сломаться
                }
            }, "Запись-" + (i + 1));
        }
        long start = System.nanoTime();
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        return new WriteResult(System.nanoTime() - start, store.getWritten(), exceptions.get());
    }

    private interface Store {
        void write(int value);

        long getWritten();
    }

    /** Общая структура данных — все операции под монитором. */
    private static final class SynchronizedStore implements Store {
        private final List<Integer> items = new ArrayList<>();
        private long written;

        @Override
        public synchronized void write(int value) {
            items.add(value);
            written++;
        }

        @Override
        public synchronized long getWritten() {
            return written;
        }
    }

    /** Та же структура данных, но без synchronized. */
    private static final class UnsynchronizedStore implements Store {
        private final List<Integer> items = new ArrayList<>();
        private long written;

        @Override
        public void write(int value) {
            items.add(value);
            written++; // соседний поток может вклиниться между шагами read -> inc -> write
        }

        @Override
        public long getWritten() {
            return written;
        }
    }

    private record WriteResult(long nanos, long written, int exceptions) {
    }

    /**
     * Этап 3. Предотвращение взаимной блокировки: два потока одновременно
     * переносят элементы навстречу друг другу. Каждый из них держит два замка,
     * но захватывает их в одном и том же порядке — метод transferItems().
     */
    private void runDeadlockSafeTransfer() throws InterruptedException {
        System.out.println("=== Предотвращение deadlock: единый порядок захвата замков ===");
        DataCollector collectorA = new DataCollector("коллектор-A");
        DataCollector collectorB = new DataCollector("коллектор-B");
        int initial = 100;
        for (int i = 0; i < initial; i++) {
            collectorA.collectItem(new Item("a-" + i));
            collectorB.collectItem(new Item("b-" + i));
        }

        Thread toB = new Thread(() -> {
            for (int i = 0; i < TRANSFER_ROUNDS; i++) {
                DataCollector.transferItems(collectorA, collectorB, TRANSFER_AMOUNT);
            }
        }, "Перенос-A-в-B");
        Thread toA = new Thread(() -> {
            for (int i = 0; i < TRANSFER_ROUNDS; i++) {
                DataCollector.transferItems(collectorB, collectorA, TRANSFER_AMOUNT);
            }
        }, "Перенос-B-в-A");

        toB.start();
        toA.start();
        toB.join();
        toA.join();

        int total = collectorA.getPendingCount() + collectorB.getPendingCount();
        System.out.println("Осталось: в " + collectorA.getName() + " — " + collectorA.getPendingCount()
                + ", в " + collectorB.getName() + " — " + collectorB.getPendingCount());
        System.out.println("Целостность данных: всего элементов по-прежнему " + total
                + " из " + 2 * initial + ": " + (total == 2 * initial));
    }
}