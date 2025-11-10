package org.spacehub.service.serviceAuth;

import org.spacehub.DTO.DTO_auth.LoginRequest;
import org.spacehub.DTO.DTO_auth.OTPRequest;
import org.spacehub.DTO.DTO_auth.RefreshRequest;
import org.spacehub.DTO.DTO_auth.ResetPasswordRequest;
import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.DTO.DTO_auth.ValidateForgotOtpRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.entities.Auth.RegistrationRequest;
import org.spacehub.entities.OTP.OtpType;
import org.spacehub.entities.User.UserRole;
import org.spacehub.security.EmailValidator;
import org.spacehub.security.PhoneNumberValidator;
import org.spacehub.service.serviceAuth.authInterfaces.IUserAccountService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService implements IUserAccountService {

  private final VerificationService verificationService;
  private final EmailValidator emailValidator;
  private final PhoneNumberValidator phoneNumberValidator;
  private final OTPService otpService;
  private final UserService userService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordEncoder passwordEncoder;
  private final RedisService redisService;
  private final UserNameService userNameService;
  private static final int TEMP_TOKEN_EXPIRE = 300;

  public UserAccountService(VerificationService verificationService,
                            EmailValidator emailValidator,
                            PhoneNumberValidator phoneNumberValidator, // New
                            OTPService otpService,
                            UserService userService,
                            RefreshTokenService refreshTokenService,
                            PasswordEncoder passwordEncoder,
                            RedisService redisService,
                            UserNameService userNameService) {
    this.verificationService = verificationService;
    this.emailValidator = emailValidator;
    this.phoneNumberValidator = phoneNumberValidator; // New
    this.otpService = otpService;
    this.userService = userService;
    this.refreshTokenService = refreshTokenService;
    this.passwordEncoder = passwordEncoder;
    this.redisService = redisService;
    this.userNameService = userNameService;
  }

  public ApiResponse<TokenResponse> login(LoginRequest request) {

    ApiResponse<TokenResponse> err = validateLoginRequest(request);
    if (err != null) {
      return err;
    }

    String rawIdentifier = request.getIdentifier();
    String normalizedIdentifier;
    User user;

    try {
      if (emailValidator.isEmail(rawIdentifier)) {
        normalizedIdentifier = emailValidator.normalize(rawIdentifier);
        user = userService.getUserByEmail(normalizedIdentifier);
      } else if (phoneNumberValidator.isPhoneNumber(rawIdentifier)) {
        normalizedIdentifier = phoneNumberValidator.normalize(rawIdentifier);
        user = userService.getUserByPhoneNumber(normalizedIdentifier);
      } else {
        return new ApiResponse<>(400, "Invalid identifier. Must be an email or phone number.",
          null);
      }
    } catch (UsernameNotFoundException e) {
      return new ApiResponse<>(404, "User not found", null);
    }

    if (!Boolean.TRUE.equals(user.getEnabled())) {
      return new ApiResponse<>(403, "Account not enabled. Please verify your OTP first.",
        null);
    }

    if (!verificationService.checkCredentials(user, request.getPassword())) {
      return new ApiResponse<>(401, "Invalid credentials", null);
    }

    TokenResponse tokens = generateTokensOrNull(user);
    if (tokens == null) {
      return new ApiResponse<>(500, "Failed to generate tokens", null);
    }

    return new ApiResponse<>(200, "Logged in successfully", tokens);
  }

  private ApiResponse<TokenResponse> validateLoginRequest(LoginRequest request) {
    if (request == null || request.getIdentifier() == null || request.getPassword() == null) {
      return new ApiResponse<>(400, "Identifier and password are required", null);
    }
    return null;
  }

  private TokenResponse generateTokensOrNull(User user) {
    try {
      return verificationService.generateTokens(user);
    } catch (Exception e) {
      return null;
    }
  }

  public ApiResponse<String> register(RegistrationRequest request) {

    if (request == null) {
      return new ApiResponse<>(400, "Registration data is required", null);
    }

    RegistrationRequest tempRegistration = new RegistrationRequest();
    tempRegistration.setFirstName(request.getFirstName());
    tempRegistration.setLastName(request.getLastName());
    tempRegistration.setPassword(passwordEncoder.encode(request.getPassword()));

    String identifier;
    try {
      identifier = processIdentifier(request, tempRegistration);
    } catch (IllegalArgumentException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    }

    ApiResponse<String> cooldownResponse = handleCooldown(identifier);
    if (cooldownResponse != null) {
      return cooldownResponse;
    }

    try {
      otpService.saveTempOtp(identifier, tempRegistration);
      otpService.sendOTP(identifier, OtpType.REGISTRATION);

      String sessionToken = userNameService.generateRegistrationToken(identifier);
      redisService.saveValue("REGISTRATION_SESSION_" + identifier, sessionToken, TEMP_TOKEN_EXPIRE);

      return new ApiResponse<>(200, "OTP sent. Complete registration by validating OTP.",
        sessionToken);
    } catch (RuntimeException e) {
      return new ApiResponse<>(500, "Registration failed: " + e.getMessage(), null);
    }
  }

  private String processIdentifier(RegistrationRequest request, RegistrationRequest tempRegistration) {
    String identifier = null;

    if (request.getEmail() != null && !request.getEmail().isBlank()) {
      String email = emailValidator.normalize(request.getEmail());
      if (!emailValidator.isEmail(email)) {
        throw new IllegalArgumentException("Invalid email format");
      }
      if (userService.existsByEmail(email)) {
        throw new IllegalArgumentException("User with this email already exists");
      }
      tempRegistration.setEmail(email);
      identifier = email;
    }

    if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
      String phone = phoneNumberValidator.normalize(request.getPhoneNumber());
      if (!phoneNumberValidator.isPhoneNumber(phone)) {
        throw new IllegalArgumentException("Invalid phone number format.");
      }
      if (userService.existsByPhoneNumber(phone)) {
        throw new IllegalArgumentException("User with this phone number already exists");
      }
      tempRegistration.setPhoneNumber(phone);

      if (identifier == null) {
        identifier = phone;
      }
    }

    if (identifier == null) {
      throw new IllegalArgumentException("Email or Phone Number is required for registration");
    }

    return identifier;
  }

  private ApiResponse<String> handleCooldown(String identifier) {
    if (otpService.isInCooldown(identifier, OtpType.REGISTRATION)) {
      long secondsLeft = otpService.cooldownTime(identifier, OtpType.REGISTRATION);
      return new ApiResponse<>(400,
        "Please wait " + secondsLeft + " seconds before requesting OTP again.", null);
    }
    return null;
  }

  public ApiResponse<?> validateOTP(OTPRequest request) {

    if (request.getIdentifier() == null || request.getOtp() == null || request.getType() == null) {
      return new ApiResponse<>(400, "Identifier, OTP, and OTP type are required.", null);
    }

    String identifier;
    try {
      identifier = normalizeIdentifier(request);
    } catch (IllegalArgumentException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    }

    OtpType type = request.getType();
    ApiResponse<?> earlyCheck = preValidateOtpChecks(identifier, type);
    if (earlyCheck != null) {
      return earlyCheck;
    }

    boolean valid = otpService.validateOTP(identifier, request.getOtp(), type);
    if (!valid) {
      return handleInvalidOtp(identifier, type);
    }

    otpService.markAsUsed(identifier, request.getOtp(), type);
    return handleRegistrationOTP(identifier);
  }

  private String normalizeIdentifier(OTPRequest request) {
    String raw = request.getIdentifier();
    if (emailValidator.isEmail(raw)) {
      return emailValidator.normalize(raw);
    }
    if (phoneNumberValidator.isPhoneNumber(raw)) {
      return phoneNumberValidator.normalize(raw);
    }
    throw new IllegalArgumentException("Invalid identifier format.");
  }

  private ApiResponse<?> preValidateOtpChecks(String identifier, OtpType type) {
    if (type != OtpType.REGISTRATION) {
      return new ApiResponse<>(400, "Only registration OTP can be validated here.", null);
    }

    if (otpService.isBlocked(identifier, type)) {
      return new ApiResponse<>(429, "Too many invalid OTP attempts. Try again later.", null);
    }

    if (otpService.isUsed(identifier, type)) {
      return new ApiResponse<>(400, "OTP has already been used", null);
    }

    return null;
  }

  private ApiResponse<?> handleInvalidOtp(String identifier, OtpType type) {
    long attempts = otpService.incrementOtpAttempts(identifier, type);
    if (attempts >= 3) {
      otpService.blockOtp(identifier, type);
      return new ApiResponse<>(429, "Too many invalid attempts. Please request a new OTP.",
        null);
    }

    return new ApiResponse<>(400, "Invalid or expired OTP. Attempts left: " + (3 - attempts),
      null);
  }

  public ApiResponse<String> forgotPassword(String identifier) {

    if (identifier == null) {
      return new ApiResponse<>(400, "Email or phone number is required", null);
    }

    String normalizedIdentifier;
    User user;
    try {
      if (emailValidator.isEmail(identifier)) {
        normalizedIdentifier = emailValidator.normalize(identifier);
        user = userService.getUserByEmail(normalizedIdentifier);
      } else if (phoneNumberValidator.isPhoneNumber(identifier)) {
        normalizedIdentifier = phoneNumberValidator.normalize(identifier);
        user = userService.getUserByPhoneNumber(normalizedIdentifier);
      } else {
        return new ApiResponse<>(200, "If this account is registered, an OTP has been sent.",
          null);
      }
    } catch (Exception e) {
      return new ApiResponse<>(200, "If this account is registered, an OTP has been sent.",
        null);
    }

    if (otpService.isInCooldown(normalizedIdentifier, OtpType.FORGOT_PASSWORD)) {
      long secondsLeft = otpService.cooldownTime(normalizedIdentifier, OtpType.FORGOT_PASSWORD);
      return new ApiResponse<>(400,
        "Please wait " + secondsLeft + " seconds before requesting OTP again.", null);
    }

    String tempToken = otpService.sendOTPWithTempToken(user, OtpType.FORGOT_PASSWORD);
    return new ApiResponse<>(200, "OTP sent to your registered email/phone", tempToken);
  }

  public ApiResponse<String> resetPassword(ResetPasswordRequest request) {
    String identifier = request.getIdentifier();
    String normalizedIdentifier;

    if (emailValidator.isEmail(identifier)) {
      normalizedIdentifier = emailValidator.normalize(identifier);
    } else if (phoneNumberValidator.isPhoneNumber(identifier)) {
      normalizedIdentifier = phoneNumberValidator.normalize(identifier);
    } else {
      return new ApiResponse<>(400, "Invalid identifier.", null);
    }

    String tempToken = request.getTempToken();
    String newPassword = request.getNewPassword();

    String savedToken = redisService.getValue("TEMP_RESET_" + normalizedIdentifier);
    if (savedToken == null || !savedToken.equals(tempToken)) {
      return new ApiResponse<>(401, "Unauthorized. OTP not validated or token expired.",
        null);
    }

    User user;
    try {
      if (emailValidator.isEmail(normalizedIdentifier)) {
        user = userService.getUserByEmail(normalizedIdentifier);
      } else {
        user = userService.getUserByPhoneNumber(normalizedIdentifier);
      }
    } catch (Exception e) {
      return new ApiResponse<>(400, "User not found", null);
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    int currentVersion;
    if (user.getPasswordVersion() != null) {
      currentVersion = user.getPasswordVersion();
    } else {
      currentVersion = 0;
    }
    user.setPasswordVersion(currentVersion + 1);
    userService.save(user);
    redisService.deleteValue("TEMP_RESET_" + normalizedIdentifier);

    return new ApiResponse<>(200, "Password has been reset successfully", null);
  }

  public ApiResponse<String> logout(RefreshRequest request) {
    if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
      return new ApiResponse<>(400, "Refresh token is required", null);
    }

    String refreshToken = request.getRefreshToken();
    boolean deleted = refreshTokenService.deleteIfExists(refreshToken);

    if (!deleted) {
      return new ApiResponse<>(404, "Refresh token not found", null);
    }

    return new ApiResponse<>(200, "Logout successful", null);
  }

  private ApiResponse<?> handleRegistrationOTP(String identifier) {
    try {
      RegistrationRequest tempRequest = otpService.getTempOtp(identifier);
      if (tempRequest == null) {
        return new ApiResponse<>(400, "Registration session expired or not found", null);
      }

      if (isUserAlreadyRegistered(tempRequest)) {
        return new ApiResponse<>(400, "User already registered", null);
      }

      User newUser = buildUserFromRequest(tempRequest);
      userService.save(newUser);

      otpService.deleteTempOtp(identifier);
      otpService.deleteOTP(identifier, OtpType.REGISTRATION);
      otpService.deleteRegistrationSessionToken(identifier);

      return new ApiResponse<>(200, "Registration verified successfully", null);

    } catch (Exception e) {
      return new ApiResponse<>(500, "Registration verification failed: " + e.getMessage(),
        null);
    }
  }

  private boolean isUserAlreadyRegistered(RegistrationRequest tempRequest) {
    if (tempRequest.getEmail() != null && userService.existsByEmail(tempRequest.getEmail())) {
      return true;
    }
    return tempRequest.getPhoneNumber() != null && userService.existsByPhoneNumber(tempRequest.getPhoneNumber());
  }

  private User buildUserFromRequest(RegistrationRequest tempRequest) {
    User newUser = new User();
    newUser.setFirstName(tempRequest.getFirstName());
    newUser.setLastName(tempRequest.getLastName());
    newUser.setPassword(tempRequest.getPassword());
    newUser.setIsVerifiedRegistration(true);
    newUser.setEnabled(true);
    newUser.setLocked(false);
    newUser.setUserRole(UserRole.USER);

    if (tempRequest.getEmail() != null) {
      newUser.setEmail(tempRequest.getEmail());
    }
    if (tempRequest.getPhoneNumber() != null) {
      newUser.setPhoneNumber(tempRequest.getPhoneNumber());
    }

    return newUser;
  }

  public ApiResponse<String> resendOTP(String identifier, String sessionToken) {
    if (identifier == null || sessionToken == null) {
      return new ApiResponse<>(400, "Identifier and session token are required", null);
    }

    String normalizedIdentifier;
    if (emailValidator.isEmail(identifier)) {
      normalizedIdentifier = emailValidator.normalize(identifier);
    } else if (phoneNumberValidator.isPhoneNumber(identifier)) {
      normalizedIdentifier = phoneNumberValidator.normalize(identifier);
    } else {
      return new ApiResponse<>(400, "Invalid identifier format.", null);
    }

    String savedToken = redisService.getValue("REGISTRATION_SESSION_" + normalizedIdentifier);
    boolean sessionValid = savedToken != null && savedToken.equals(sessionToken);

    if (!sessionValid) {
      return new ApiResponse<>(403, "Invalid or expired registration session token", null);
    }

    if (isUserAlreadyVerified(normalizedIdentifier)) {
      return new ApiResponse<>(400, "User already verified. No OTP needed.", null);
    }

    if (otpService.isInCooldown(normalizedIdentifier, OtpType.REGISTRATION)) {
      long secondsLeft = otpService.cooldownTime(normalizedIdentifier, OtpType.REGISTRATION);
      return new ApiResponse<>(400, "Please wait " + secondsLeft +
        " seconds before requesting OTP again.", null);
    }

    return attemptSendOtp(normalizedIdentifier);
  }

  public ApiResponse<TokenResponse> validateForgotPasswordOtp(
    ValidateForgotOtpRequest request) {
    String rawIdentifier = request.getIdentifier();
    String normalizedIdentifier;

    if (emailValidator.isEmail(rawIdentifier)) {
      normalizedIdentifier = emailValidator.normalize(rawIdentifier);
    } else if (phoneNumberValidator.isPhoneNumber(rawIdentifier)) {
      normalizedIdentifier = phoneNumberValidator.normalize(rawIdentifier);
    } else {
      return new ApiResponse<>(400, "Invalid identifier format.", null);
    }

    String otp = request.getOtp();

    if (otpService.isBlocked(normalizedIdentifier, OtpType.FORGOT_PASSWORD)) {
      return new ApiResponse<>(429, "Too many invalid OTP attempts. Try again later.",
        null);
    }

    if (otpService.isUsed(normalizedIdentifier, OtpType.FORGOT_PASSWORD)) {
      return new ApiResponse<>(400, "OTP has already been used", null);
    }

    boolean valid = otpService.validateOTP(normalizedIdentifier, otp, OtpType.FORGOT_PASSWORD);
    if (!valid) {
      long attempts = otpService.incrementOtpAttempts(normalizedIdentifier, OtpType.FORGOT_PASSWORD);
      if (attempts >= 3) {
        otpService.blockOtp(normalizedIdentifier, OtpType.FORGOT_PASSWORD);
        return new ApiResponse<>(429, "Too many invalid OTP attempts. Request a new OTP.",
          null);
      }
      return new ApiResponse<>(400, "Invalid or expired OTP. Attempts left: " + (3 - attempts),
        null);
    }

    otpService.markAsUsed(normalizedIdentifier, otp, OtpType.FORGOT_PASSWORD);
    User user;
    try {
      if (emailValidator.isEmail(normalizedIdentifier)) {
        user = userService.getUserByEmail(normalizedIdentifier);
      } else {
        user = userService.getUserByPhoneNumber(normalizedIdentifier);
      }
    } catch (Exception e) {
      return new ApiResponse<>(400, "User not found", null);
    }

    String tempToken = userNameService.generateToken(user);
    redisService.saveValue("TEMP_RESET_" + normalizedIdentifier, tempToken, TEMP_TOKEN_EXPIRE);
    TokenResponse tokenResponse = new TokenResponse(tempToken, null);

    return new ApiResponse<>(200, "OTP validated successfully", tokenResponse);
  }

  public ApiResponse<String> resendForgotPasswordOtp(String tempToken) {
    String identifier = otpService.extractIdentifierFromToken(tempToken, OtpType.FORGOT_PASSWORD);

    if (identifier == null) {
      return new ApiResponse<>(403, "Session expired or invalid", null);
    }

    if (otpService.isInCooldown(identifier, OtpType.FORGOT_PASSWORD)) {
      long secondsLeft = otpService.cooldownTime(identifier, OtpType.FORGOT_PASSWORD);
      return new ApiResponse<>(400, "Please wait " + secondsLeft +
        " seconds before requesting OTP again.", null);
    }

    User user;
    try {
      if (emailValidator.isEmail(identifier)) {
        user = userService.getUserByEmail(identifier);
      } else {
        user = userService.getUserByPhoneNumber(identifier);
      }
    } catch (Exception e) {
      return new ApiResponse<>(404, "User not found", null);
    }

    try {
      String newTempToken = otpService.sendOTPWithTempToken(user, OtpType.FORGOT_PASSWORD);
      return new ApiResponse<>(200, "OTP resent successfully.", newTempToken);
    } catch (RuntimeException e) {
      return new ApiResponse<>(429, e.getMessage(), null);
    }
  }

  private boolean isUserAlreadyVerified(String identifier) {
    try {
      User existingUser;
      if (emailValidator.isEmail(identifier)) {
        existingUser = userService.getUserByEmail(identifier);
      } else {
        existingUser = userService.getUserByPhoneNumber(identifier);
      }
      return existingUser != null && Boolean.TRUE.equals(existingUser.getEnabled());
    } catch (Exception ignored) {
      return false;
    }
  }

  private ApiResponse<String> attemptSendOtp(String identifier) {
    try {
      otpService.sendOTP(identifier, OtpType.REGISTRATION);
      return new ApiResponse<>(200, "OTP resent successfully.", null);
    } catch (RuntimeException e) {
      return new ApiResponse<>(429, e.getMessage(), null);
    }
  }
}
