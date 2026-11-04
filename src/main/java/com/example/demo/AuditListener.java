package com.example.demo;

/**
 * Слушатель-аудитор: печатает только покупки. Добавляется в систему
 * в середине прогона — подписка на лету итерации не мешает.
 */
public class AuditListener implements UserEventListener {

    @Override
    public void onEvent(UserEvent event) {
        if ("PURCHASE".equals(event.type())) {
            System.out.println("[аудит] покупка: " + event.userId() + " / " + event.type());
        }
    }
}