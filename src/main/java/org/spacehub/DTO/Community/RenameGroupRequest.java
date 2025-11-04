package org.spacehub.DTO.Community;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameGroupRequest {
    private String requesterEmail;
    private String newGroupName;
}
