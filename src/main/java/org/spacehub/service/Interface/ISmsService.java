package org.spacehub.service.Interface;

// Assumed interface org.spacehub.service.Interface.ISmsService
public interface ISmsService {
  void sendSms(String phoneNumber, String otp); // Changed 'message' to 'otp'
}

