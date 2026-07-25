package com.poupito.api.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

	@Mock
	private JavaMailSender javaMailSender;

	@Test
	void loggingSenderShouldNotThrow() {
		LoggingEmailSender sender = new LoggingEmailSender();

		assertThatCode(() -> sender.send("victor@poupito.com", "Assunto", "Corpo"))
				.doesNotThrowAnyException();
	}

	@Test
	void smtpSenderShouldDispatchSimpleMailMessage() {
		SmtpEmailSender sender = new SmtpEmailSender(javaMailSender, "nao-responda@poupito.com");

		sender.send("victor@poupito.com", "Assunto", "Corpo");

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(javaMailSender).send(captor.capture());
		SimpleMailMessage message = captor.getValue();
		assertThat(message.getFrom()).isEqualTo("nao-responda@poupito.com");
		assertThat(message.getTo()).containsExactly("victor@poupito.com");
		assertThat(message.getSubject()).isEqualTo("Assunto");
		assertThat(message.getText()).isEqualTo("Corpo");
	}

}
