package org.spacehub.service.service_auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String fromEmail;

  public EmailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Async
  public void sendEmail(String to, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(to);
    message.setSubject("OTP Verification");
    message.setText(body);
    mailSender.send(message);
  }

}
