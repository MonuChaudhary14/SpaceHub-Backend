package org.spacehub.service.serviceAuth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.OTP.OtpType;
import org.spacehub.entities.Auth.RegistrationRequest;
import org.spacehub.entities.User.User;
import org.spacehub.security.EmailValidator;
import org.spacehub.security.PhoneNumberValidator;
import org.spacehub.service.SmsService;
import org.spacehub.service.serviceAuth.authInterfaces.IEmailService;
import org.spacehub.service.serviceAuth.authInterfaces.IOTPService;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;

@Service
public class OTPService implements IOTPService {

  private final RedisService redisService;
  private final IEmailService emailService;
  private final SmsService smsService;
  private final EmailValidator emailValidator;
  private final PhoneNumberValidator phoneNumberValidator;
  private final VerificationService verificationService;
  private static final SecureRandom random = new SecureRandom();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final int OTP_GENERATE_LIMIT = 5;
  private static final int OTP_LIMIT_WINDOW_SECONDS = 900;
  private static final int OTP_EXPIRE_SECONDS = 300;
  private static final int COOLDOWN_SECONDS = 30;
  private static final int TEMP_REGISTRATION_EXPIRE = 600;
  private static final int BLOCK_DURATION = 300;
  private static final int TEMP_TOKEN_EXPIRE = 2592000;

  public OTPService(RedisService redisService, IEmailService emailService, SmsService smsService, EmailValidator emailValidator,
                    PhoneNumberValidator phoneNumberValidator, VerificationService verificationService) {
    this.redisService = redisService;
    this.emailService = emailService;
    this.smsService = smsService;
    this.emailValidator = emailValidator;
    this.phoneNumberValidator = phoneNumberValidator;
    this.verificationService = verificationService;
  }

  private String generateOtp() {
    int otp = 100000 + random.nextInt(900000);
    return String.valueOf(otp);
  }

  public void sendOTP(String identifier, OtpType type) {
    if (hasExceededOtpLimit(identifier, type)) {
      long remaining = remainingLimitTime(identifier, type);
      throw new RuntimeException("OTP request limit exceeded. Try again after " + remaining + " seconds.");
    }

    String usedKey = "OTP_USED_" + type + "_" + identifier;
    redisService.deleteValue(usedKey);

    String otp = generateOtp();
    String otpKey = "OTP_" + type + "_" + identifier;
    String cooldownKey = "OTP_COOLDOWN_" + type + "_" + identifier;

    redisService.saveValue(otpKey, otp, OTP_EXPIRE_SECONDS);
    redisService.saveValue(cooldownKey, "1", COOLDOWN_SECONDS);


    if (emailValidator.isEmail(identifier)) {
      String message = "Your OTP is: " + otp + ". It will expire in 5 minutes.";
      emailService.sendEmail(identifier, message);
    } else if (phoneNumberValidator.isPhoneNumber(identifier)) {
      smsService.sendSms(identifier, otp);
    } else {
      throw new RuntimeException("Invalid identifier type for sending OTP.");
    }
  }

  public boolean validateOTP(String identifier, String otp, OtpType type) {
    String key = "OTP_" + type + "_" + identifier;
    String savedOtp = redisService.getValue(key);
    return savedOtp != null && savedOtp.equals(otp);
  }

  public boolean isUsed(String identifier, OtpType type) {
    String key = "OTP_USED_" + type + "_" + identifier;
    return redisService.exists(key);
  }

  public void markAsUsed(String identifier, String otp, OtpType type) {
    String usedKey = "OTP_USED_" + type + "_" + identifier;
    redisService.saveValue(usedKey, otp, OTP_EXPIRE_SECONDS);

    String otpKey = "OTP_" + type + "_" + identifier;
    redisService.deleteValue(otpKey);

    String attemptKey = "OTP_ATTEMPTS_" + type + "_" + identifier;
    redisService.deleteValue(attemptKey);
  }

  public boolean isInCooldown(String identifier, OtpType type) {
    String key = "OTP_COOLDOWN_" + type + "_" + identifier;
    Long liveTime = redisService.getLiveTime(key);
    return liveTime != null && liveTime > 0;
  }

  public long cooldownTime(String identifier, OtpType type) {
    String key = "OTP_COOLDOWN_" + type + "_" + identifier;
    Long liveTime = redisService.getLiveTime(key);
    return (liveTime != null && liveTime > 0) ? liveTime : 0;
  }

  public void saveTempOtp(String identifier, RegistrationRequest request) {
    try {
      String convertedKey = objectMapper.writeValueAsString(request);
      String key = "REGISTRATION_TEMP_" + identifier;
      redisService.saveValue(key, convertedKey, TEMP_REGISTRATION_EXPIRE);
    }
    catch (Exception e) {
      throw new RuntimeException("Failed to save temporary registration", e);
    }
  }

  public RegistrationRequest getTempOtp(String identifier) {
    try {
      String key = "REGISTRATION_TEMP_" + identifier;
      String json = redisService.getValue(key);
      if (json == null) return null;
      return objectMapper.readValue(json, RegistrationRequest.class);
    } catch (Exception e) {
      return null;
    }
  }

  public void deleteTempOtp(String identifier) {
    String key = "REGISTRATION_TEMP_" + identifier;
    redisService.deleteValue(key);
  }

  public void deleteOTP(String identifier, OtpType type) {
    String key = "OTP_" + type + "_" + identifier;
    redisService.deleteValue(key);
  }

  public long incrementOtpAttempts(String identifier, OtpType type) {
    String attemptKey = "OTP_ATTEMPTS_" + type + "_" + identifier;
    Long attempts = redisService.incrementValue(attemptKey);
    if (attempts == 1) redisService.setExpiry(attemptKey, BLOCK_DURATION);
    return attempts;
  }

  public void blockOtp(String identifier, OtpType type) {
    String blockKey = "OTP_BLOCKED_" + type + "_" + identifier;
    redisService.saveValue(blockKey, "1", BLOCK_DURATION);
  }

  public boolean isBlocked(String identifier, OtpType type) {
    String blockKey = "OTP_BLOCKED_" + type + "_" + identifier;
    return redisService.exists(blockKey);
  }

  public String sendOTPWithTempToken(User user, OtpType type) {
    String identifier;
    if (user.getEmail() != null && !user.getEmail().isBlank()) {
      identifier = user.getEmail();
    } else if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
      identifier = user.getPhoneNumber();
    } else {
      throw new RuntimeException("User has no email or phone number to send OTP to.");
    }

    sendOTP(identifier, type);
    var tokenResponse = verificationService.generateTokens(user);

    String tempTokenKey = "TEMP_TOKEN_" + type + "_" + identifier;
    redisService.deleteValue(tempTokenKey);
    redisService.saveValue(tempTokenKey, tokenResponse.getAccessToken(), TEMP_TOKEN_EXPIRE);

    String tokenToIdentifierKey = type.name() + "_" + tokenResponse.getAccessToken();
    redisService.saveValue(tokenToIdentifierKey, identifier, TEMP_TOKEN_EXPIRE);

    return tokenResponse.getAccessToken();
  }

  public String extractIdentifierFromToken(String tempToken, OtpType type) {
    String key = type.name() + "_" + tempToken;
    return redisService.getValue(key);
  }

  public void deleteRegistrationSessionToken(String token) {
    if (token == null) {
      return;
    }
    String key = "REG_SESSION_" + token;
    redisService.deleteValue(key);
  }

  private String otpLimitKey(String identifier, OtpType type) {
    return "OTP_LIMIT_" + type + "_" + identifier;
  }

  private boolean hasExceededOtpLimit(String identifier, OtpType type) {
    String limitKey = otpLimitKey(identifier, type);
    Long attempts = redisService.incrementValue(limitKey);
    if (attempts == 1) {
      redisService.setExpiry(limitKey, OTP_LIMIT_WINDOW_SECONDS);
    }
    return attempts > OTP_GENERATE_LIMIT;
  }

  private long remainingLimitTime(String identifier, OtpType type) {
    return redisService.getLiveTime(otpLimitKey(identifier, type));
  }

}
