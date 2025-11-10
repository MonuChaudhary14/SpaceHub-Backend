package org.spacehub.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = EmailOrPhoneValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface EmailOrPhone {
  String message() default "Either email or phone number must be provided, but not both blank";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
