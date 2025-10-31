package org.spacehub.service.service_auth;

import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.security.EmailValidator;
import org.spacehub.security.PhoneNumberValidator;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

  private final UserRepository userRepository;
  private final EmailValidator emailValidator;
  private final PhoneNumberValidator phoneNumberValidator;

  public UserService(UserRepository userRepository,
                     EmailValidator emailValidator,
                     PhoneNumberValidator phoneNumberValidator) {
    this.userRepository = userRepository;
    this.emailValidator = emailValidator;
    this.phoneNumberValidator = phoneNumberValidator;
  }

  @Override
  public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
    if (emailValidator.isEmail(identifier)) {
      return userRepository.findByEmail(emailValidator.normalize(identifier))
        .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + identifier));
    } else if (phoneNumberValidator.isPhoneNumber(identifier)) {
      return userRepository.findByPhoneNumber(phoneNumberValidator.normalize(identifier))
        .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + identifier));
    }

    throw new UsernameNotFoundException("Invalid identifier. Must be email or phone: " + identifier);
  }

  public User getUserByEmail(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  public void save(User user) {
    userRepository.save(user);
  }

  public boolean existsByEmail(String email) {
    return userRepository.existsByEmail(email);
  }

  public User getUserByPhoneNumber(String phoneNumber) throws UsernameNotFoundException {
    return userRepository.findByPhoneNumber(phoneNumber)
      .orElseThrow(() -> new UsernameNotFoundException("User not found with phone number: " + phoneNumber));
  }

  public boolean existsByPhoneNumber(String phoneNumber) {
    return userRepository.existsByPhoneNumber(phoneNumber);
  }
}
