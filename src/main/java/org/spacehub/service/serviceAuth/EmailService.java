package org.spacehub.service.serviceAuth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.spacehub.service.serviceAuth.authInterfaces.IEmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService implements IEmailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String fromEmail;

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

  @Async
  public void sendCustomEmail(String to, String subject, String messageBody) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);

      helper.setFrom(fromEmail);
      helper.setTo(to);
      helper.setSubject(subject);

      String htmlContent = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; 
                    padding: 25px; background: #ffffff; border-radius: 12px; 
                    border: 1px solid #e6e6e6;">

            <div style="text-align: center; margin-bottom: 20px;">
                <img src="https://img.icons8.com/?size=80&id=23264&format=png" 
                     alt="SpaceHub" style="width: 70px; opacity: 0.9;">
                <h2 style="color: #4A90E2; margin-top: 10px;">Stay Connected with SpaceHub 🚀</h2>
            </div>

            <p style="font-size: 16px; color: #333;">
                Hey there! 👋<br><br>
                Thank you for being a part of the <strong>SpaceHub Community</strong>.
                We're constantly working to bring you:
            </p>

            <ul style="font-size: 15px; color: #444; line-height: 1.7;">
                <li>📢 New community updates</li>
                <li>📚 Inspiring stories from members</li>
                <li>✨ Exciting new features and improvements</li>
                <li>🛠 Better tools to enhance your SpaceHub experience</li>
                <li>🚀 Product updates & future roadmap</li>
            </ul>

            <p style="font-size: 16px; color: #333; margin-top: 20px;">
                %s
            </p>

            <div style="margin-top: 25px; text-align: center;">
                <a href="https://www.spacehubx.me/" 
                   style="background: #4A90E2; color: white; padding: 12px 20px; 
                          border-radius: 8px; text-decoration: none; font-size: 15px;">
                   Visit SpaceHub
                </a>
            </div>

            <hr style="margin-top: 30px; border: none; border-top: 1px solid #eee;">
            
        </div>
        """.formatted(messageBody);

      helper.setText(htmlContent, true);
      mailSender.send(message);

    } catch (MessagingException e) {
      throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
    }
  }

}
