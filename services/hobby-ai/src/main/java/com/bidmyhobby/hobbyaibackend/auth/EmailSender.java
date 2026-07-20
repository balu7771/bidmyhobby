package com.bidmyhobby.hobbyaibackend.auth;

public interface EmailSender {
    String sendLoginCode(String email, String code);
    String sendMessage(String email, String subject, String content);
}
