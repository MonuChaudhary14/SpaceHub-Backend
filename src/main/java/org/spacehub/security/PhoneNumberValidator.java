package org.spacehub.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PhoneNumberValidator {

  private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");

  public String normalize(String phoneNumber) {
    if (phoneNumber == null) {
      return null;
    }
    return phoneNumber.replaceAll("[\\s\\-()]", "").trim();
  }

  public boolean isPhoneNumber(String identifier) {
    if (identifier == null) {
      return false;
    }
    String normalized = normalize(identifier);
    return PHONE_PATTERN.matcher(normalized).matches();
  }
}
