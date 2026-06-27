package com.rzodeczko.infrastructure.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JavaEmailAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private JavaEmailAdapter adapter;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> messageCaptor;

    @Test
    @DisplayName("should create SimpleMailMessage with correct fields and call mailSender.send")
    void shouldCreateMessageWithCorrectFieldsAndSend() {
        // given
        var to = "user@example.com";
        var subject = "Activation Code";
        var body = "Your activation code: 123456\nValid for: 300 seconds";

        // when
        adapter.send(to, subject, body);

        // then
        verify(mailSender).send(messageCaptor.capture());
        var captured = messageCaptor.getValue();
        assertThat(captured.getTo()).containsExactly(to);
        assertThat(captured.getSubject()).isEqualTo(subject);
        assertThat(captured.getText()).isEqualTo(body);
    }
}
