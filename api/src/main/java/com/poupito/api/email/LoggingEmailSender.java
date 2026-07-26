package com.poupito.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sender default (quando {@code app.mail.enabled=false}): apenas registra o email no log em vez
 * de enviar de verdade. Permite desenvolver e testar o fluxo de recuperação de senha sem depender
 * do SES/DNS — o link de reset aparece no log da API.
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String to, String subject, String body) {
		log.info("[EMAIL:log] para={} assunto={}\n{}", to, subject, body);
	}

}
