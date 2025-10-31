package org.spacehub.service.service_auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);

      helper.setFrom(fromEmail);
      helper.setTo(to);
      helper.setSubject("Your OTP Code");

      String htmlContent = """
          <div style="font-family: Arial, sans-serif; max-width: 500px; margin: auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
            <h2 style="color: #4A90E2; text-align: center;"> SpaceHub OTP Verification</h2>
            <p style="font-size: 15px;">Use the following One-Time Password (OTP) to verify your account. The OTP will expire in <b>5 minutes</b>.</p>
            <div style="text-align: center; margin: 30px 0;">
              <span style="font-size: 28px; letter-spacing: 4px; background: #f3f4f6; padding: 10px 20px; border-radius: 8px; display: inline-block; color: #333; font-weight: bold;">
                %s
              </span>
            </div>
            <p style="font-size: 14px; color: #555;">If you did not request this, you can safely ignore this email.</p>
            <hr style="border: none; border-top: 1px solid #eee;">
            <p style="text-align: center; font-size: 13px; color: #aaa;">&copy; 2025 SpaceHub. All rights reserved.</p>
          </div>
          """.formatted(body);

      helper.setText(htmlContent, true);
      mailSender.send(message);

    } catch (MessagingException e) {
      throw new RuntimeException("Failed to send OTP email: " + e.getMessage(), e);
    }
  }
}
