package com.example.financemanager.notification.infrastructure.mail;

import com.example.financemanager.notification.application.MailService;
import com.example.financemanager.notification.domain.MailMessage;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;
    private final MailTemplateRenderer templateRenderer;
    private final MailProperties properties;

    public SmtpMailService(
            JavaMailSender mailSender,
            MailTemplateRenderer templateRenderer,
            MailProperties properties
    ) {
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.properties = properties;
    }

    @Override
    public void send(MailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(templateRenderer.render(message), true);

            mailSender.send(mimeMessage);
        } catch (Exception exception) {
            throw new MailDeliveryException("Unable to send email", exception);
        }
    }
}