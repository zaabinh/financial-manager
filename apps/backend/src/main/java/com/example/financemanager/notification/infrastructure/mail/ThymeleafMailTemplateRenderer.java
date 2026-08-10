package com.example.financemanager.notification.infrastructure.mail;

import com.example.financemanager.notification.domain.MailMessage;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class ThymeleafMailTemplateRenderer
        implements MailTemplateRenderer {

    private final TemplateEngine templateEngine;

    public ThymeleafMailTemplateRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public String render(MailMessage message) {
        Context context = new Context();
        context.setVariables(message.templateVariables());

        return templateEngine.process(
                "mail/" + message.templateName(),
                context
        );
    }
}