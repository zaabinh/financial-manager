package com.example.financemanager.notification.infrastructure.mail;

import com.example.financemanager.notification.domain.MailMessage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpMailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock MailTemplateRenderer templateRenderer;

    private SmtpMailService service;

    @BeforeEach
    void setUp() {
        service = new SmtpMailService(
                mailSender,
                templateRenderer,
                new MailProperties("no-reply@example.com", "Finance Manager")
        );
    }

    @Test
    void sendsRenderedHtmlMessageThroughJavaMailSender() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(
                Session.getInstance(new Properties())
        );
        MailMessage message = new MailMessage(
                "user@example.com",
                "Verify your email",
                "verify-email",
                Map.of("verificationUrl", "http://localhost/verify")
        );
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateRenderer.render(message)).thenReturn("<p>Verify</p>");

        service.send(message);

        ArgumentCaptor<MimeMessage> messageCaptor =
                ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("Verify your email");
        assertThat(messageCaptor.getValue().getAllRecipients())
                .extracting(Object::toString)
                .containsExactly("user@example.com");
        verify(templateRenderer).render(message);
    }
}
