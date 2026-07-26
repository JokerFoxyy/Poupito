package com.poupito.api.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sender real (quando {@code app.mail.enabled=true}): envia via SMTP (AWS SES). Requer
 * {@code spring.mail.*} configurado (host/port/username/password) — ver
 * {@code D:/Docs/Poupito/setup-ses-email.md}.
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(String to, String subject, String body) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(body);
		mailSender.send(message);
	}

}
