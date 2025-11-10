package org.spacehub.service.serviceAuth.authInterfaces;

public interface ISmsService {
  void sendSms(String phoneNumber, String otp);
}

