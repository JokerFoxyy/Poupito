package com.poupito.api.email;

/**
 * Abstração de envio de email transacional. Duas implementações são selecionadas por
 * {@code app.mail.enabled}: {@link LoggingEmailSender} (default, dev/test/prod-sem-SES) e
 * {@link SmtpEmailSender} (SMTP/AWS SES). Nos testes é mockada.
 */
public interface EmailSender {

	void send(String to, String subject, String body);

}
