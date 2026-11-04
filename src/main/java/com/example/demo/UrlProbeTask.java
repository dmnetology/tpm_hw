package com.example.demo;

import java.util.concurrent.Callable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Задача для пула потоков: отправляет HTTP-запрос по одному URL, замеряет
 * время ответа и возвращает результат. Ошибки сети не прерывают работу —
 * попадают в результат с описанием.
 */
public class UrlProbeTask implements Callable<ProbeResult> {

    private final RestTemplate restTemplate;
    private final String url;

    public UrlProbeTask(RestTemplate restTemplate, String url) {
        this.restTemplate = restTemplate;
        this.url = url;
    }

    @Override
    public ProbeResult call() {
        long start = System.nanoTime();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new ProbeResult(url, Thread.currentThread().getName(), elapsed,
                    response.getStatusCode().value(), null);
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new ProbeResult(url, Thread.currentThread().getName(), elapsed, 0, e.getMessage());
        }
    }
}