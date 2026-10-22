package com.socialcoisa.rede.notifications.application;

/**
 * Publicado quando uma notificacao e criada. Quem quiser reagir (ex.: enviar push) escuta este
 * evento fora da transacao da criacao.
 */
public record NotificationCreatedEvent(String userId, String kind, String title, String body, String href) {
}
