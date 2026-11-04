package com.example.demo;

/**
 * Результат проверки одного URL: поток, который выполнял запрос,
 * статус-код и время ответа; при ошибке — код 0 и её описание.
 */
public record ProbeResult(String url, String threadName, long elapsedMs, int statusCode, String error) {

    public boolean succeeded() {
        return error == null;
    }
}