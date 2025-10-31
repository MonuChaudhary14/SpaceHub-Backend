package org.spacehub.entities.User;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

@Data
@EqualsAndHashCode
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User implements UserDetails {

  @SequenceGenerator(
    name = "user_sequence",
    sequenceName = "user_sequence",
    allocationSize = 1
  )
  @Id
  @GeneratedValue(
    strategy = GenerationType.SEQUENCE,
    generator = "user_sequence"
  )
  private Long id;

  private String firstName;
  private String lastName;
  @Column(unique = true)
  @JsonProperty("username")
  private String username;
  @Column(unique = true, nullable = true)
  private String email;
  private String password;
  @Enumerated(EnumType.STRING)
  private UserRole userRole;
  private Boolean locked = false;
  private Boolean enabled = false;
  private Boolean isVerifiedRegistration = false;
  private Boolean isVerifiedLogin = false;
  private Boolean isVerifiedForgot = false;
  private Integer passwordVersion = 0;
  @Column(unique = true)
  private String phoneNumber;
  private String avatarUrl;
  private String coverPhotoUrl;
  private String bio;
  private String location;
  private String website;
  private LocalDate dateOfBirth;

  private Integer followersCount = 0;
  private Integer followingCount = 0;

  private Boolean isPrivate = false;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public User(String firstName,
                 String lastName,
                 String email,
                 String password,
                 UserRole userRole) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.password = password;
    this.userRole = userRole;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(userRole.name());
    return Collections.singletonList(authority);
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return this.email != null ? this.email : this.phoneNumber;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !locked;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }


}

