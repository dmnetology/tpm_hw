package com.example.demo;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Потребитель «сервера задач»: забирает URL-задачи из ограниченной
 * BlockingQueue, скачивает страницу RestTemplate'ом и извлекает заголовок
 * &lt;title&gt;. Опустошение обрабатывается честно: poll с таймаутом вместо
 * бесконечного take() — при пустой очереди потребитель ждёт, но не навечно.
 */
public class UrlConsumer implements Runnable {

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final long POLL_TIMEOUT_MS = 1000;

    private final String name;
    private final BlockingQueue<UrlTask> queue;
    private final AtomicBoolean producing;
    private final RestTemplate restTemplate;
    private final AtomicInteger processedCount = new AtomicInteger();

    public UrlConsumer(String name, BlockingQueue<UrlTask> queue, AtomicBoolean producing,
                       RestTemplate restTemplate) {
        this.name = name;
        this.queue = queue;
        this.producing = producing;
        this.restTemplate = restTemplate;
    }

    @Override
    public void run() {
        try {
            // потребитель работает, пока производители не закончили и в очереди остались задачи
            while (producing.get() || !queue.isEmpty()) {
                UrlTask task = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (task == null) {
                    System.out.println("[" + name + "] очередь пуста — жду задачи");
                    continue;
                }
                process(task);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** «Скачивает страницу и извлекает заголовок» — по примеру из задания. */
    private void process(UrlTask task) {
        long start = System.nanoTime();
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(task.url(), HttpMethod.GET, null, String.class);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            Matcher matcher = TITLE_PATTERN.matcher(response.getBody());
            String title = matcher.find() ? matcher.group(1).trim() : "не найден";
            processedCount.incrementAndGet();
            System.out.println("[" + name + "] " + task.url() + " — заголовок: «" + title + "» (" + elapsed + " мс)");
        } catch (Exception e) {
            processedCount.incrementAndGet();
            System.out.println("[" + name + "] " + task.url() + " — ошибка: " + e.getMessage());
        }
    }

    public int getProcessedCount() {
        return processedCount.get();
    }
}