package com.example.demo;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса: сравнение производительности последовательного stream()
 * и параллельного parallelStream() на списке из 1 000 000 случайных чисел.
 * Для каждого варианта выполняются фильтрация (только чётные),
 * преобразование (умножение на 2) и агрегация (сумма), замеряется время.
 */
@Service
public class StreamBenchmarkService {

    private static final int SIZE = 1_000_000;
    private static final int RUNS = 3;

    /** Демонстрация измерения stream() и parallelStream(), вызывается при старте приложения. */
    public void runStreamBenchmark() {
        System.out.println("=== stream() vs parallelStream(): фильтрация, преобразование, агрегация ===");
        System.out.println("Количество чисел: " + SIZE);
        System.out.println("Ядер у процессора: " + Runtime.getRuntime().availableProcessors()
                + ", параллелизм ForkJoinPool.commonPool(): " + ForkJoinPool.getCommonPoolParallelism());

        List<Integer> numbers = new Random().ints(SIZE).boxed().toList();

        // прогрев JIT: первые запуски не показательны и не замеряются
        sumSequential(numbers);
        sumParallel(numbers);

        long seqNanos = 0;
        long parNanos = 0;
        long seqSum = 0;
        long parSum = 0;
        for (int i = 1; i <= RUNS; i++) {
            long start = System.nanoTime();
            seqSum = sumSequential(numbers);
            long seqTime = System.nanoTime() - start;

            start = System.nanoTime();
            parSum = sumParallel(numbers);
            long parTime = System.nanoTime() - start;

            seqNanos += seqTime;
            parNanos += parTime;
            System.out.printf("Запуск %d: stream() %.1f мс, parallelStream() %.1f мс%n",
                    i, seqTime / 1e6, parTime / 1e6);
        }

        System.out.printf("Среднее: stream() %.1f мс, parallelStream() %.1f мс%n",
                seqNanos / (double) RUNS / 1e6, parNanos / (double) RUNS / 1e6);
        System.out.println("Сумма: последовательный = " + seqSum
                + ", параллельный = " + parSum + ", совпадают: " + (seqSum == parSum));
    }

    private long sumSequential(List<Integer> numbers) {
        return numbers.stream()
                .filter(n -> n % 2 == 0)  // фильтрация: только чётные
                .mapToLong(n -> n * 2L)   // преобразование: умножение на 2
                .sum();                   // агрегация: сумма
    }

    private long sumParallel(List<Integer> numbers) {
        return numbers.parallelStream()
                .filter(n -> n % 2 == 0)
                .mapToLong(n -> n * 2L)
                .sum();
    }
}