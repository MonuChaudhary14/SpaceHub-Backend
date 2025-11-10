package org.spacehub.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.spacehub.entities.Auth.RegistrationRequest;

public class EmailOrPhoneValidator implements ConstraintValidator<EmailOrPhone, RegistrationRequest> {

  @Override
  public boolean isValid(RegistrationRequest request, ConstraintValidatorContext context) {
    if (request == null) {
      return true;
    }

    boolean hasEmail = request.getEmail() != null && !request.getEmail().isBlank();
    boolean hasPhone = request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank();

    return hasEmail || hasPhone;
  }
}
