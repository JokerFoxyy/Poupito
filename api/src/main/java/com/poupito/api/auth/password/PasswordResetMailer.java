package com.poupito.api.auth.password;

import com.poupito.api.email.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Monta e envia o email branded de redefinição de senha, com o link contendo o token. */
@Component
public class PasswordResetMailer {

	private static final String SUBJECT = "Poupito — redefinição de senha";

	private final EmailSender emailSender;
	private final String resetUrlBase;

	public PasswordResetMailer(EmailSender emailSender,
			@Value("${app.frontend.reset-url-base}") String resetUrlBase) {
		this.emailSender = emailSender;
		this.resetUrlBase = resetUrlBase;
	}

	public void sendResetLink(String email, String rawToken) {
		String link = resetUrlBase + "?token=" + rawToken;
		String body = """
				Olá,

				Recebemos um pedido para redefinir a senha da sua conta Poupito.
				Para criar uma nova senha, acesse o link abaixo (válido por tempo limitado):

				%s

				Se você não pediu isso, ignore este email — sua senha continua a mesma.

				— Poupito. Descomplique, poupe, Poupito.""".formatted(link);
		emailSender.send(email, SUBJECT, body);
	}

}
