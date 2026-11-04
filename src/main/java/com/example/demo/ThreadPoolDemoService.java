package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Класс сервиса для демонстрации пула потоков. Список публичных API
 * проверяется двумя способами: последовательно и через собственный
 * ThreadPoolExecutor, настроенный вручную; выводится сводка статус-кодов,
 * времён ответа и состояния пула.
 */
@Service
public class ThreadPoolDemoService {

    private static final List<String> URLS = List.of(
            "https://jsonplaceholder.typicode.com/posts/1",
            "https://jsonplaceholder.typicode.com/users/1",
            "https://jsonplaceholder.typicode.com/todos/1",
            "https://jsonplaceholder.typicode.com/albums/1",
            "https://jsonplaceholder.typicode.com/comments/1",
            "https://api.github.com/zen",
            "https://api.github.com/octocat",
            "https://api.github.com/licenses/mit",
            "https://api.binance.com/api/v3/ping",
            "https://api.binance.com/api/v3/time",
            "https://api.frankfurter.app/latest",
            "https://api.open-meteo.com/v1/forecast?latitude=55.75&longitude=37.62&current_weather=true",
            "https://api.open-meteo.com/v1/forecast?latitude=57.15&longitude=65.53&current_weather=true",
            "https://catfact.ninja/fact",
            "https://catfact.ninja/facts?limit=1",
            "https://api.chucknorris.io/jokes/random",
            "https://v2.jokeapi.dev/joke/Any",
            "https://api.thecatapi.com/v1/images/search",
            "https://randomuser.me/api/?inc=gender",
            "https://api.sunrise-sunset.org/json?lat=55.75&lng=37.62");

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 10;
    private static final int TIMEOUT_MS = 5000;

    /** Демонстрация пула потоков, вызывается при старте приложения. */
    public void runThreadPoolDemo() {
        try {
            runSequentialProbing();
            runPoolProbing();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Демонстрация прервана");
        }
    }

    /** Время последовательной проверки — база для сравнения с пулом. */
    private long sequentialElapsedMs;

    /** Этап 1. Последовательная проверка всех URL — база для сравнения. */
    private void runSequentialProbing() {
        System.out.println("=== Последовательная проверка всех " + URLS.size() + " URL ===");
        RestTemplate restTemplate = restTemplate();
        long start = System.nanoTime();
        int successes = 0;
        int errors = 0;
        for (String url : URLS) {
            try {
                restTemplate.exchange(url, HttpMethod.GET, null, String.class);
                successes++;
            } catch (Exception e) {
                errors++;
            }
        }
        sequentialElapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Проверено " + URLS.size() + " URL за " + sequentialElapsedMs + " мс"
                + " (успешных ответов: " + successes + ", ошибок: " + errors + ")");
    }

    /**
     * Этап 2. Собственный ThreadPoolExecutor, настроенный вручную:
     * отправляет запросы ко всем URL одновременно, собирает результаты
     * по Future и выводит сводку.
     */
    private void runPoolProbing() throws InterruptedException {
        System.out.println("=== Собственный ThreadPoolExecutor: " + URLS.size() + " URL одновременно ===");
        System.out.println("Настройка пула: corePoolSize=" + CORE_POOL_SIZE + ", maximumPoolSize=" + MAX_POOL_SIZE
                + ", keepAliveTime=30 с, очередь ArrayBlockingQueue(" + QUEUE_CAPACITY + ")"
                + ", при переполнении CallerRunsPolicy");

        RestTemplate restTemplate = restTemplate();
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                task -> {
                    Thread thread = new Thread(task, "UrlProbe-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());

        List<Future<ProbeResult>> futures = new ArrayList<>();
        long start = System.nanoTime();
        for (String url : URLS) {
            futures.add(pool.submit(new UrlProbeTask(restTemplate, url)));
        }
        Thread.sleep(100); // снимок пула под нагрузкой: задачи ещё выполняются
        System.out.println("Пул под нагрузкой: в пуле " + pool.getPoolSize()
                + " потоков, активных " + pool.getActiveCount() + ", в очереди " + pool.getQueue().size());

        pool.shutdown();
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
            System.out.println("Пул не завершился за 60 с");
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        int successes = 0;
        int errors = 0;
        long sumTime = 0;
        for (Future<ProbeResult> future : futures) {
            ProbeResult result;
            try {
                result = future.get();
            } catch (ExecutionException e) {
                continue; // не должно случиться: задача сама перехватывает все исключения
            }
            sumTime += result.elapsedMs();
            if (result.succeeded()) {
                successes++;
            } else {
                errors++;
            }
            if (result.succeeded()) {
                System.out.println(result.threadName() + ": " + result.url()
                        + " — код " + result.statusCode() + ", " + result.elapsedMs() + " мс");
            } else {
                System.out.println(result.threadName() + ": " + result.url()
                        + " — ошибка за " + result.elapsedMs() + " мс: " + result.error());
            }
        }
        System.out.println("Сводка: успешных ответов " + successes + " из " + URLS.size()
                + ", ошибок: " + errors);
        System.out.println("Пул после работы: потоков создано " + pool.getLargestPoolSize()
                + ", задач завершено " + pool.getCompletedTaskCount()
                + ", shutdown() отработал: " + pool.isTerminated());
        System.out.printf("Время: последовательно %d мс, с пулом %d мс — ускорение примерно в %.1f раза%n",
                sequentialElapsedMs, elapsed, sequentialElapsedMs / (double) Math.max(1, elapsed));
    }

    /** RestTemplate с тайм-аутами сети, чтобы зависший URL не растягивал сводку. */
    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}