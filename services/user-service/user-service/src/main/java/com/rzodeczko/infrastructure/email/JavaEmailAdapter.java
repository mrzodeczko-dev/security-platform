package com.rzodeczko.infrastructure.email;

import com.rzodeczko.application.port.EmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JavaEmailAdapter implements EmailPort {
    private final JavaMailSender mailSender;

    @Override
    public void send(String to, String subject, String body) {
        log.info("Sending email to={}, subject={}", to, subject);
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
