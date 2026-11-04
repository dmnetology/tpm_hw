package com.example.demo;

/**
 * Слушатель событий системы: получает каждое обработанное событие.
 */
@FunctionalInterface
public interface UserEventListener {

    void onEvent(UserEvent event);
}