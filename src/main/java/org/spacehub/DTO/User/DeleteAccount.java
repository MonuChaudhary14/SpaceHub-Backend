package org.spacehub.DTO.User;

import lombok.Data;

@Data
public class DeleteAccount {

    private String email;
    private String currentPassword;

}
