package org.spacehub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.service.Interface.ISmsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SmsService implements ISmsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SmsService.class);
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${twofactor.api.key}")
  private String apiKey;

  @Override
  public void sendSms(String phoneNumber, String otp) { // <-- Changed 'message' to 'otp'
    try {
      // IMPORTANT: Make sure phoneNumber does NOT include a '+' sign
      // (e.g., "919876543210" is good, "+919876543210" is bad for a URL)
      String mPhoneNumber = phoneNumber.replace("+", "");

      // Use the correct API endpoint for sending a pre-generated OTP
      String url = String.format(
        "https://2factor.in/API/V1/%s/SMS/%s/%s",
        apiKey, mPhoneNumber, otp
      );

      LOGGER.info("Attempting to call 2Factor URL: {}", url);

      String response = restTemplate.getForObject(url, String.class);
      LOGGER.info("OTP sent to {} | Response: {}", mPhoneNumber, response);

    } catch (Exception e) {
      LOGGER.error("Failed to send OTP via 2Factor: {}", e.getMessage(), e); // Log the full exception
      throw new RuntimeException("Failed to send OTP", e);
    }
  }
}
