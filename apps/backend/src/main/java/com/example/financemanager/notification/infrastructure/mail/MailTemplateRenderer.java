package com.example.financemanager.notification.infrastructure.mail;

import com.example.financemanager.notification.domain.MailMessage;

public interface MailTemplateRenderer {

    String render(MailMessage message);
}