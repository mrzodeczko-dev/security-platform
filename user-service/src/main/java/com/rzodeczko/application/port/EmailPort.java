package com.rzodeczko.application.port;

public interface EmailPort {
    void send(String to, String subject, String body);
}
