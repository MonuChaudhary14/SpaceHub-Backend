package org.spacehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class FriendDTO {

    private String name;
    private String email;
    private String profileImage;

}
