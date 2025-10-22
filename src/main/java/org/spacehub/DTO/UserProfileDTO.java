package org.spacehub.DTO;

import lombok.Data;

@Data
public class UserProfileDTO {

    private String firstName;
    private String lastName;
    private String bio;
    private String location;
    private String website;
    private String dateOfBirth;
    private Boolean isPrivate;

}
