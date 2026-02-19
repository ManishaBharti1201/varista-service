package com.varista.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class EmailUtils {

    @Autowired
    private JavaMailSender mailSender;

    public void sendPasswordResetEmail(String toEmail, String password) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("bhanuchandra2010@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Your Password Reset Request");

        // Setting the body with the password from UserInfo
        message.setText("Hello,\n\nYour current password is: " + password +
                "\n\nPlease log in and change your password immediately for security.");

        mailSender.send(message);
    }
}
