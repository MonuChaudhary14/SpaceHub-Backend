package org.spacehub.service_auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.OtpType;
import org.spacehub.entities.RegistrationRequest;
import org.spacehub.entities.User;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.UUID;

@Service
public class OTPService {

  private final RedisService redisService;
  private final EmailService emailService;
  private final VerificationService verificationService;
  private static final SecureRandom random = new SecureRandom();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final int OTP_GENERATE_LIMIT = 5;
  private static final int OTP_LIMIT_WINDOW_SECONDS = 900;
  private static final int OTP_EXPIRE_SECONDS = 300;
  private static final int COOLDOWN_SECONDS = 30;
  private static final int TEMP_REGISTRATION_EXPIRE = 600;
  private static final int BLOCK_DURATION = 300;
  private static final int TEMP_TOKEN_EXPIRE = 600;
  private static final int REG_SESSION_EXPIRE = TEMP_REGISTRATION_EXPIRE;

  public OTPService(RedisService redisService, EmailService emailService, VerificationService verificationService) {
    this.redisService = redisService;
    this.emailService = emailService;
    this.verificationService = verificationService;
  }

  private String generateOtp() {
    int otp = 100000 + random.nextInt(900000);
    return String.valueOf(otp);
  }

  public void sendOTP(String email, OtpType type) {
    if (hasExceededOtpLimit(email, type)) {
      long remaining = remainingLimitTime(email, type);
      throw new RuntimeException("OTP request limit exceeded. Try again after " + remaining + " seconds.");
    }

    String usedKey = "OTP_USED_" + type + "_" + email;
    redisService.deleteValue(usedKey);

    String otp = generateOtp();
    String otpKey = "OTP_" + type + "_" + email;
    String cooldownKey = "OTP_COOLDOWN_" + type + "_" + email;

    redisService.saveValue(otpKey, otp, OTP_EXPIRE_SECONDS);
    redisService.saveValue(cooldownKey, "1", COOLDOWN_SECONDS);

    emailService.sendEmail(email, "Your OTP is: " + otp + ". It will expire in 5 minutes.");
  }

  public boolean validateOTP(String email, String otp, OtpType type) {
    String key = "OTP_" + type + "_" + email;
    String savedOtp = redisService.getValue(key);
    return savedOtp != null && savedOtp.equals(otp);
  }

  public boolean isUsed(String email, OtpType type) {
    String key = "OTP_USED_" + type + "_" + email;
    return redisService.exists(key);
  }

  public void markAsUsed(String email, String otp, OtpType type) {
    String usedKey = "OTP_USED_" + type + "_" + email;
    redisService.saveValue(usedKey, otp, OTP_EXPIRE_SECONDS);

    String otpKey = "OTP_" + type + "_" + email;
    redisService.deleteValue(otpKey);

    String attemptKey = "OTP_ATTEMPTS_" + type + "_" + email;
    redisService.deleteValue(attemptKey);
  }

  public boolean isInCooldown(String email, OtpType type) {
    String key = "OTP_COOLDOWN_" + type + "_" + email;
    Long liveTime = redisService.getLiveTime(key);
    return liveTime != null && liveTime > 0;
  }

  public long cooldownTime(String email, OtpType type) {
    String key = "OTP_COOLDOWN_" + type + "_" + email;
    Long liveTime = redisService.getLiveTime(key);
    return (liveTime != null && liveTime > 0) ? liveTime : 0;
  }

  public void saveTempOtp(String email, RegistrationRequest request) {
    try {
      String convertedKey = objectMapper.writeValueAsString(request);
      String key = "REGISTRATION_TEMP_" + email;
      redisService.saveValue(key, convertedKey, TEMP_REGISTRATION_EXPIRE);
    }
    catch (Exception e) {
      throw new RuntimeException("Failed to save temporary registration", e);
    }
  }

  public RegistrationRequest getTempOtp(String email) {
    try {
      String key = "REGISTRATION_TEMP_" + email;
      String json = redisService.getValue(key);
      if (json == null) return null;
      return objectMapper.readValue(json, RegistrationRequest.class);
    } catch (Exception e) {
      return null;
    }
  }

  public void deleteTempOtp(String email) {
    String key = "REGISTRATION_TEMP_" + email;
    redisService.deleteValue(key);
  }

  public void deleteOTP(String email, OtpType type) {
    String key = "OTP_" + type + "_" + email;
    redisService.deleteValue(key);
  }

  public long incrementOtpAttempts(String email, OtpType type) {
    String attemptKey = "OTP_ATTEMPTS_" + type + "_" + email;
    Long attempts = redisService.incrementValue(attemptKey);
    if (attempts == 1) redisService.setExpiry(attemptKey, BLOCK_DURATION);
    return attempts;
  }

  public void blockOtp(String email, OtpType type) {
    String blockKey = "OTP_BLOCKED_" + type + "_" + email;
    redisService.saveValue(blockKey, "1", BLOCK_DURATION);
  }

  public boolean isBlocked(String email, OtpType type) {
    String blockKey = "OTP_BLOCKED_" + type + "_" + email;
    return redisService.exists(blockKey);
  }

  public String sendOTPWithTempToken(User user, OtpType type) {
    sendOTP(user.getEmail(), type);
    var tokenResponse = verificationService.generateTokens(user);

    String tempTokenKey = "TEMP_TOKEN_" + type + "_" + user.getEmail();
    redisService.deleteValue(tempTokenKey);
    redisService.saveValue(tempTokenKey, tokenResponse.getAccessToken(), TEMP_TOKEN_EXPIRE);

    String tokenToEmailKey = type.name() + "_" + tokenResponse.getAccessToken();
    redisService.saveValue(tokenToEmailKey, user.getEmail(), TEMP_TOKEN_EXPIRE);

    return tokenResponse.getAccessToken();
  }

  public String extractEmailFromToken(String tempToken, OtpType type) {
    String key = type.name() + "_" + tempToken;
    return redisService.getValue(key);
  }

  public String createRegistrationSessionToken(String email) {
    String token = UUID.randomUUID().toString();
    String key = "REG_SESSION_" + token;
    redisService.saveValue(key, email, REG_SESSION_EXPIRE);
    return token;
  }

  public boolean validateRegistrationSessionToken(String token, String email) {
    if (token == null || token.isBlank() || email == null) {
      return false;
    }
    String key = "REG_SESSION_" + token;
    String mapped = redisService.getValue(key);
    return mapped != null && mapped.equals(email);
  }

  public void deleteRegistrationSessionToken(String token) {
    if (token == null) {
      return;
    }
    String key = "REG_SESSION_" + token;
    redisService.deleteValue(key);
  }

  private String otpLimitKey(String email, OtpType type) {
    return "OTP_LIMIT_" + type + "_" + email;
  }

  private boolean hasExceededOtpLimit(String email, OtpType type) {
    String limitKey = otpLimitKey(email, type);
    Long attempts = redisService.incrementValue(limitKey);
    if (attempts == 1) {
      redisService.setExpiry(limitKey, OTP_LIMIT_WINDOW_SECONDS);
    }
    return attempts > OTP_GENERATE_LIMIT;
  }

  private long remainingLimitTime(String email, OtpType type) {
    return redisService.getLiveTime(otpLimitKey(email, type));
  }

}
