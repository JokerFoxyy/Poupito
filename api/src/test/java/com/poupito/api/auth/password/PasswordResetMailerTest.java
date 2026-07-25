package com.poupito.api.auth.password;

import com.poupito.api.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailerTest {

	@Mock
	private EmailSender emailSender;

	@Test
	void shouldSendEmailWithResetLinkContainingToken() {
		PasswordResetMailer mailer = new PasswordResetMailer(emailSender,
				"https://poupito.com/redefinir-senha");

		mailer.sendResetLink("victor@poupito.com", "tok-123");

		ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(emailSender).send(to.capture(), subject.capture(), body.capture());

		assertThat(to.getValue()).isEqualTo("victor@poupito.com");
		assertThat(subject.getValue()).contains("Poupito");
		assertThat(body.getValue())
				.contains("https://poupito.com/redefinir-senha?token=tok-123");
	}

}
