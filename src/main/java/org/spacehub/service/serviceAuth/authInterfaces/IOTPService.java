package org.spacehub.service.serviceAuth.authInterfaces;

import org.spacehub.entities.OTP.OtpType;
import org.spacehub.entities.Auth.RegistrationRequest;
import org.spacehub.entities.User.User;

public interface IOTPService {

  void sendOTP(String email, OtpType type);

  boolean validateOTP(String email, String otp, OtpType type);

  boolean isUsed(String email, OtpType type);

  void markAsUsed(String email, String otp, OtpType type);

  boolean isInCooldown(String email, OtpType type);

  long cooldownTime(String email, OtpType type);

  void saveTempOtp(String email, RegistrationRequest request);

  RegistrationRequest getTempOtp(String email);

  void deleteTempOtp(String email);

  void deleteOTP(String email, OtpType type);

  long incrementOtpAttempts(String email, OtpType type);

  void blockOtp(String email, OtpType type);

  boolean isBlocked(String email, OtpType type);

  String sendOTPWithTempToken(User user, OtpType type);

  String extractEmailFromToken(String tempToken, OtpType type);

  void deleteRegistrationSessionToken(String token);

}

