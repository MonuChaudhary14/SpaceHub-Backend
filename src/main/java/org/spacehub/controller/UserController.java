package org.spacehub.controller;

import jakarta.validation.Valid;
import org.spacehub.DTO.DTO_auth.ForgotPasswordRequest;
import org.spacehub.DTO.DTO_auth.LoginRequest;
import org.spacehub.DTO.DTO_auth.OTPRequest;
import org.spacehub.DTO.DTO_auth.RefreshRequest;
import org.spacehub.DTO.DTO_auth.ResendForgotOtpRequest;
import org.spacehub.DTO.DTO_auth.ResendOtpRequest;
import org.spacehub.DTO.DTO_auth.ResetPasswordRequest;
import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.DTO.DTO_auth.ValidateForgotOtpRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Auth.RegistrationRequest;
import org.spacehub.service.service_auth.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(path = "api/v1")
public class UserController {

  private final UserAccountService accountService;

  public UserController(UserAccountService accountService) {
    this.accountService = accountService;
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest request) {
    ApiResponse<TokenResponse> resp = accountService.login(request);
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/registration")
  public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegistrationRequest request) {
    ApiResponse<String> resp = accountService.register(request);
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/validateregisterotp")
  public ResponseEntity<ApiResponse<?>> validateOTP(@RequestBody OTPRequest request) {
    ApiResponse<?> resp = accountService.validateOTP(request);
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/forgotpassword")
  public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
    ApiResponse<String> resp = accountService.forgotPassword(request.getEmail());
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/validateforgototp")
  public ResponseEntity<ApiResponse<TokenResponse>> validateForgotOtp(@RequestBody ValidateForgotOtpRequest request) {
    ApiResponse<TokenResponse> resp = accountService.validateForgotPasswordOtp(request);
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/resetpassword")
  public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordRequest request) {
    ApiResponse<String> resp = accountService.resetPassword(request);
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<String>> logout(@RequestBody(required = false) RefreshRequest request) {
    ApiResponse<String> resp = accountService.logout(request);
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/resendotp")
  public ResponseEntity<ApiResponse<String>> resendOTP(@RequestBody ResendOtpRequest request) {
    ApiResponse<String> resp = accountService.resendOTP(request.getEmail(), request.getSessionToken());
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/resendforgototp")
  public ResponseEntity<ApiResponse<String>> resendForgotPasswordOtp(@RequestBody ResendForgotOtpRequest request) {
    ApiResponse<String> resp = accountService.resendForgotPasswordOtp(request.getTempToken());
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/signal")
  public ResponseEntity<String> handleSignal(@RequestBody Map<String, Object> payload) {
    System.out.println("Received signal: " + payload);
    return ResponseEntity.ok("Signal received");
  }


}
