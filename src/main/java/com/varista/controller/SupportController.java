package com.varista.controller;

import com.varista.model.SupportTicketRequest;
import com.varista.utils.EmailUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/support")
@Slf4j
public class SupportController {
    private final EmailUtils emailUtils;

    public SupportController(EmailUtils emailUtils) {
        this.emailUtils = emailUtils;
    }

    @PostMapping("/tickets")
    public ResponseEntity<Map<String, String>> submitTicket(
            @Valid @RequestBody SupportTicketRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String contactEmail = jwt.getClaimAsString("email");
        if (contactEmail == null || contactEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Your account email is unavailable."));
        }

        try {
            emailUtils.sendSupportTicket(
                    request.getContactName(),
                    contactEmail,
                    request.getTenantName(),
                    request.getSubject(),
                    request.getDescription());
            return ResponseEntity.accepted().body(Map.of("message", "Support request submitted."));
        } catch (MailAuthenticationException exception) {
            log.error("Support ticket email authentication failed; verify SMTP credential configuration.", exception);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "message",
                            "The email server rejected its configured credentials. Verify SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD. Gmail SMTP requires an App Password."));
        } catch (MailException exception) {
            log.error("Support ticket email delivery failed", exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Unable to send the support request. Please try again later."));
        }
    }
}
