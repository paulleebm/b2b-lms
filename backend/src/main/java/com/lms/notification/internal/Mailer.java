package com.lms.notification.internal;

import jakarta.mail.MessagingException;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * HTML mail from Thymeleaf templates ({@code templates/mail/*.html}). Locally SMTP goes to
 * Mailpit (http://localhost:8025); in AWS to the SES SMTP endpoint (credentials from SSM).
 * Only called from the worker, never inside a user's request.
 */
@Component
class Mailer {

    private final JavaMailSender sender;
    private final ITemplateEngine templates;
    private final String from;
    private final String appUrl;

    Mailer(JavaMailSender sender, ITemplateEngine templates, @Value("${lms.mail.from}") String from,
           @Value("${lms.keycloak.app-url}") String appUrl) {
        this.sender = sender;
        this.templates = templates;
        this.from = from;
        this.appUrl = appUrl;
    }

    void send(String to, String subject, String template, Map<String, Object> variables) {
        Context context = new Context(Locale.KOREAN);
        context.setVariables(variables);
        context.setVariable("appUrl", appUrl);
        String html = templates.process("mail/" + template, context);
        var message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build mail", e);
        }
        sender.send(message);
    }
}
