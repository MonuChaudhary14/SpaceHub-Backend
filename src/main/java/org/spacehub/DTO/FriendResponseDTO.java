package org.spacehub.DTO;

public class FriendResponseDTO {

    private boolean success;
    private String message;

    public FriendResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

}
