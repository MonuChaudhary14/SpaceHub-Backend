package org.spacehub.service.ChatRoom;

import org.spacehub.DTO.chatroom.*;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoom.ChatMessageRepository;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomUserService chatRoomUserService;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           ChatMessageRepository chatMessageRepository,
                           ChatRoomUserService chatRoomUserService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomUserService = chatRoomUserService;
    }

    public ApiResponse<RoomResponseDTO> createRoom(CreateRoomRequest requestDTO) {
        ChatRoom room = ChatRoom.builder().roomName(requestDTO.getRoomName()).roomCode(UUID.randomUUID().toString())
                .createdBy(requestDTO.getCreatedBy())
                .build();
        chatRoomRepository.save(room);
        chatRoomUserService.addUserToRoom(room, requestDTO.getCreatedBy(), Role.ADMIN);
        return new ApiResponse<>(200, "Room created successfully", RoomResponseDTO.fromEntity(room));
    }

    public ApiResponse<ChatRoom> getRoomByCodeData(String roomCode) {
        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        return optionalRoom.map(room -> new ApiResponse<>(200, "Room fetched successfully", room))
                .orElseGet(() -> new ApiResponse<>(404, "Room not found", null));
    }

    public ApiResponse<List<ChatRoom>> getAllRoomsData() {
        List<ChatRoom> rooms = chatRoomRepository.findAll();
        return new ApiResponse<>(200, "Fetched all rooms", rooms);
    }

    public ApiResponse<String> deleteRoomResponse(String roomCode, String userId) {

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> optionalUser = chatRoomUserService.getUserInRoom(room, userId);
        if (optionalUser.isEmpty()) return new ApiResponse<>(403, "You are not a member of this room", null);

        Role role = optionalUser.get().getRole();

        if (role != Role.ADMIN && role != Role.WORKSPACE_OWNER)
            return new ApiResponse<>(403, "You are not authorized to delete this room", null);

        chatMessageRepository.deleteAll(chatMessageRepository.findByRoomOrderByTimestampAsc(room));

        chatRoomUserService.getMembers(room).forEach(user -> chatRoomUserService.removeUserFromRoom(room, user.getUserId()));
        chatRoomRepository.delete(room);

        return new ApiResponse<>(200, "Room deleted successfully", "Room with code " + roomCode + " deleted.");
    }

    public ApiResponse<String> joinRoomResponse(String roomCode, String userId) {

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> existingUser = chatRoomUserService.getUserInRoom(room, userId);
        if (existingUser.isPresent()) {
            return new ApiResponse<>(400, "User already in the room", null);
        }

        chatRoomUserService.addUserToRoom(room, userId, Role.MEMBER);
        return new ApiResponse<>(200, "User added to room successfully", "User " + userId + " added to room " + roomCode);
    }

    public ApiResponse<String> removeMember(RemoveMemberRequest requestDTO) {

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> optionalRequester = chatRoomUserService.getUserInRoom(room, requestDTO.getRequesterId());
        if (optionalRequester.isEmpty()) return new ApiResponse<>(403, "You are not a member of this room", null);

        ChatRoomUser requester = optionalRequester.get();
        Role requesterRole = requester.getRole();

        Optional<ChatRoomUser> optionalTarget = chatRoomUserService.getUserInRoom(room, requestDTO.getTargetUserId());
        if (optionalTarget.isEmpty()) return new ApiResponse<>(404, "Target user not found in this room", null);

        ChatRoomUser target = optionalTarget.get();
        Role targetRole = target.getRole();

        if (requestDTO.getRequesterId().equals(requestDTO.getTargetUserId()))
            return new ApiResponse<>(400, "You cannot remove yourself from the room", null);

        if (requesterRole == Role.ADMIN || (requesterRole == Role.WORKSPACE_OWNER && targetRole != Role.ADMIN && targetRole != Role.WORKSPACE_OWNER)) {
            chatRoomUserService.removeUserFromRoom(room, requestDTO.getTargetUserId());
            return new ApiResponse<>(200, "Member removed successfully","User " + target.getUserId() + " removed from room " + room.getRoomCode());
        }

        return new ApiResponse<>(403, "You are not authorized to remove members", null);
    }

    public ApiResponse<String> changeRole(ChangeRoleRequest requestDTO) {

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> optionalRequester = chatRoomUserService.getUserInRoom(room, requestDTO.getRequesterId());
        if (optionalRequester.isEmpty()) return new ApiResponse<>(403, "You are not a member of this room", null);

        ChatRoomUser requester = optionalRequester.get();
        Role requesterRole = requester.getRole();

        Optional<ChatRoomUser> optionalTarget = chatRoomUserService.getUserInRoom(room, requestDTO.getTargetUserId());
        if (optionalTarget.isEmpty()) return new ApiResponse<>(404, "Target user not found in this room", null);

        ChatRoomUser target = optionalTarget.get();
        Role targetRole = target.getRole();

        if (requestDTO.getRequesterId().equals(requestDTO.getTargetUserId()))
            return new ApiResponse<>(400, "You cannot change your own role", null);

        if (requesterRole == Role.ADMIN ||
                (requesterRole == Role.WORKSPACE_OWNER && targetRole != Role.ADMIN && targetRole != Role.WORKSPACE_OWNER)) {
            target.setRole(requestDTO.getNewRole());
            chatRoomUserService.saveUser(target);
            return new ApiResponse<>(200, "Role updated successfully","User " + target.getUserId() + " is now " + target.getRole());
        }

        return new ApiResponse<>(403, "You are not authorized to change roles", null);
    }

    public Optional<ChatRoom> findByRoomCode(String roomCode) {
        return chatRoomRepository.findByRoomCode(roomCode);
    }

    public ApiResponse<String> leaveRoom(LeaveRoomRequest requestDTO) {

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isEmpty()) {
            return new ApiResponse<>(404, "Room not found", null);
        }

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> optionalUser = chatRoomUserService.getUserInRoom(room, requestDTO.getUserId());
        if (optionalUser.isEmpty()) {
            return new ApiResponse<>(400, "You are not a member of this room", null);
        }

        chatRoomUserService.removeUserFromRoom(room, requestDTO.getUserId());
        return new ApiResponse<>(200, "Left room successfully", "User " + requestDTO.getUserId() + " has left room " + room.getRoomCode());
    }

}
