package com.example.demo;

/**
 * Слушатель-логгер: печатает каждое событие.
 */
public class LoggingListener implements UserEventListener {

    @Override
    public void onEvent(UserEvent event) {
        System.out.println("[лог] " + event.userId() + " / " + event.type());
    }
}