package org.spacehub.service.service_auth.authInterfaces;

public interface IEmailService {
  void sendEmail(String to, String body);
}

