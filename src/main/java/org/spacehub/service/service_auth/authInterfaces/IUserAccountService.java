package org.spacehub.service.service_auth.authInterfaces;

import org.spacehub.DTO.DTO_auth.*;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Auth.RegistrationRequest;

public interface IUserAccountService {

  ApiResponse<TokenResponse> login(LoginRequest request);

  ApiResponse<String> register(RegistrationRequest request);

  ApiResponse<?> validateOTP(OTPRequest request);

  ApiResponse<String> forgotPassword(String email);

  ApiResponse<String> resetPassword(ResetPasswordRequest request);

  ApiResponse<String> logout(RefreshRequest request);

  ApiResponse<String> resendOTP(String email, String sessionToken);

  ApiResponse<TokenResponse> validateForgotPasswordOtp(ValidateForgotOtpRequest request);

  ApiResponse<String> resendForgotPasswordOtp(String tempToken);

}
