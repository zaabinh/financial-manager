package com.example.financemanager.notification.domain;

import java.util.Map;

public record MailMessage(
    String recipient,
    String subject,
    String templateName,
    Map<String, Object> templateVariables
) {
}