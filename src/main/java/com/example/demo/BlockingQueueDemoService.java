package com.example.demo;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Класс сервиса для демонстрации BlockingQueue и шаблона producer-consumer:
 * «сервер задач» парсинга URL. Два производителя кладут URL-задачи
 * в ограниченную ArrayBlockingQueue — put() ждёт места при переполнении;
 * два потребителя забирают задачи poll() с таймаутом — честная обработка
 * опустошения — и скачивают страницы, извлекая заголовок.
 */
@Service
public class BlockingQueueDemoService {

    private static final List<String> FIRST_URLS = List.of(
            "https://netology.ru/",
            "https://www.interfax.ru/",
            "https://www.mos.ru/",
            "https://vk.com/",
            "https://www.cbr.ru/");
    private static final List<String> SECOND_URLS = List.of(
            "https://ria.ru/",
            "https://lenta.ru/",
            "https://rg.ru/",
            "https://www.cnews.ru/",
            "https://ok.ru/");

    private static final int QUEUE_CAPACITY = 3;
    private static final int CONSUMERS = 2;
    private static final int FETCH_TIMEOUT_MS = 5000;

    /** Демонстрация BlockingQueue, вызывается при старте приложения. */
    public void runBlockingQueueDemo() throws InterruptedException {
        System.out.println("=== BlockingQueue и producer-consumer: сервер парсинга URL ===");
        System.out.println("Очередь ArrayBlockingQueue(" + QUEUE_CAPACITY + "): "
                + "2 производителя по " + FIRST_URLS.size() + " URL, " + CONSUMERS + " потребителя");

        BlockingQueue<UrlTask> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        AtomicBoolean producing = new AtomicBoolean(true);
        RestTemplate restTemplate = restTemplate();

        // потребители стартуют первыми — до появления задач очередь пуста
        UrlConsumer first = new UrlConsumer("consumer-1", queue, producing, restTemplate);
        UrlConsumer second = new UrlConsumer("consumer-2", queue, producing, restTemplate);
        Thread firstThread = new Thread(first, "consumer-1");
        Thread secondThread = new Thread(second, "consumer-2");
        firstThread.start();
        secondThread.start();

        AtomicInteger producerSeq = new AtomicInteger();
        ExecutorService producers = Executors.newFixedThreadPool(2,
                task -> new Thread(task, "producer-" + producerSeq.incrementAndGet()));
        try {
            producers.submit(new UrlProducer("producer-1", queue, FIRST_URLS));
            producers.submit(new UrlProducer("producer-2", queue, SECOND_URLS));
        } finally {
            producers.shutdown();
            producers.awaitTermination(30, TimeUnit.SECONDS);
        }

        producing.set(false); // все задачи поданы: потребители дообрабатывают остаток и завершаются
        firstThread.join();
        secondThread.join();

        int total = first.getProcessedCount() + second.getProcessedCount();
        System.out.println("Сводка: обработано задач " + total + " из " + (FIRST_URLS.size() + SECOND_URLS.size())
                + "; consumer-1 — " + first.getProcessedCount()
                + ", consumer-2 — " + second.getProcessedCount());
    }

    /** RestTemplate с тайм-аутами сети — как в задании 8. */
    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(FETCH_TIMEOUT_MS);
        factory.setReadTimeout(FETCH_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}