package org.spacehub.service.serviceAuth.authInterfaces;

public interface IEmailService {
  void sendEmail(String to, String body);
  void sendCustomEmail(String to, String subject, String messageBody);
}

