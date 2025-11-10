package org.spacehub.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class EmailValidator {

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
    "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
    Pattern.CASE_INSENSITIVE);

  public String normalize(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase();
  }

  public boolean isEmail(String identifier) {
    if (identifier == null) {
      return false;
    }
    return EMAIL_PATTERN.matcher(identifier).matches();
  }
}
