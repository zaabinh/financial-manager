package com.example.financemanager.notification.application;

import com.example.financemanager.notification.domain.MailMessage;

public interface MailService {
    void send(MailMessage message);
}
