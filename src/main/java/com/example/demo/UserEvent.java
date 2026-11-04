package com.example.demo;

/**
 * Событие пользователя веб-приложения: кто и что сделал.
 */
public record UserEvent(String userId, String type) {}