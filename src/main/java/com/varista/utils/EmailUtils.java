package com.varista.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


@Service
public class EmailUtils {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${varista.support.email:bhanuchandra2010@gmail.com}")
    private String supportAddress;

    public void sendPasswordResetCode(String toEmail, String verificationCode) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Your password reset verification code");
        message.setText("Use this verification code to reset your password: " + verificationCode
                + "\n\nThe code expires in 10 minutes. If you did not request a password reset, you can ignore this email.");

        mailSender.send(message);
    }

    public void sendSupportTicket(
            String contactName,
            String contactEmail,
            String tenantName,
            String subject,
            String description) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(supportAddress);
        message.setReplyTo(contactEmail);
        message.setSubject("Syncra support request: " + subject);
        message.setText(
                "A technical support request was submitted.\n\n"
                        + "Contact: " + contactName + "\n"
                        + "Reply-to: " + contactEmail + "\n"
                        + "Workspace: " + tenantName + "\n"
                        + "Subject: " + subject + "\n\n"
                        + "Description:\n" + description);
        mailSender.send(message);
    }
}
