package com.rpb.reservation.customerauth.integration;

import com.rpb.reservation.customerauth.application.CustomerAuthError;
import com.rpb.reservation.customerauth.application.CustomerAuthServiceException;
import com.rpb.reservation.customerauth.application.CustomerEmailDeliveryMessage;
import com.rpb.reservation.customerauth.application.CustomerEmailSettings;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailDeliveryPort;
import java.time.Duration;
import java.util.Properties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class SmtpCustomerEmailDeliveryAdapter implements CustomerEmailDeliveryPort {

    @Override
    public void sendLoginCode(CustomerEmailDeliveryMessage message, CustomerEmailSettings settings) {
        if (settings == null || !"smtp".equals(settings.provider())) {
            throw new CustomerAuthServiceException(CustomerAuthError.EMAIL_CHANNEL_NOT_CONFIGURED);
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.smtpHost());
        sender.setPort(settings.smtpPort());
        sender.setUsername(settings.smtpUsername());
        sender.setPassword(settings.smtpPassword());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(hasText(settings.smtpUsername()) && hasText(settings.smtpPassword())));
        properties.put("mail.smtp.starttls.enable", String.valueOf(settings.smtpStartTls()));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(settings.fromEmail());
        mail.setTo(message.toEmail());
        String action=switch(message.purpose()){
            case "reset_password" -> "password reset";
            case "bind_email" -> "recovery email verification";
            default -> "reservation login";
        };
        mail.setSubject("Your " + action + " code");
        long minutes = Math.max(1, Duration.between(java.time.Instant.now(), message.expiresAt()).toMinutes());
        mail.setText("Your %s code is %s. It expires in %d minutes. Do not share this code. If you did not request this, ignore this message.".formatted(action,message.code(), minutes));

        try {
            sender.send(mail);
        } catch (MailException exception) {
            throw new CustomerAuthServiceException(CustomerAuthError.EMAIL_DELIVERY_FAILED);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
