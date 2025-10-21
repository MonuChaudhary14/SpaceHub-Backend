package org.spacehub.service;

import org.spacehub.DTO.CreateRoomRequest;
import org.spacehub.DTO.RoleChangeAction;
import org.spacehub.DTO.RoomMemberAction;
import org.spacehub.DTO.RoomResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatMessageRepository;
import org.spacehub.repository.ChatRoomRepository;
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

        ChatRoom room = ChatRoom.builder().name(requestDTO.getName()).roomCode(UUID.randomUUID().toString()).build();
        chatRoomRepository.save(room);

        chatRoomUserService.addUserToRoom(room, requestDTO.getUserId(), Role.ADMIN);

        RoomResponseDTO responseDTO = RoomResponseDTO.builder().roomCode(room.getRoomCode()).name(room.getName()).message("Room created successfully").build();

        return new ApiResponse<>(200, "Room created successfully", responseDTO);
    }

    public ApiResponse<ChatRoom> getRoomByCodeData(String roomCode) {
        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (optionalRoom.isPresent()) {
            return new ApiResponse<>(200, "Room fetched successfully", optionalRoom.get());
        }
        else {
            return new ApiResponse<>(404, "Room not found", null);
        }
    }

    public ApiResponse<List<ChatRoom>> getAllRoomsData() {
        List<ChatRoom> rooms = chatRoomRepository.findAll();
        return new ApiResponse<>(200, "Fetched all rooms", rooms);
    }

    public ApiResponse<String> deleteRoomResponse(String roomCode, String userId) {
        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (optionalRoom.isEmpty()) return new ApiResponse<>(403, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> userOpt = chatRoomUserService.getUserInRoom(room, userId);
        if (userOpt.isEmpty()) return new ApiResponse<>(403, "You are not a member of this room", null);

        Role role = userOpt.get().getRole();
        if (role != Role.ADMIN && role != Role.WORKSPACE_OWNER) {
            return new ApiResponse<>(403, "You are not authorized to delete this room", null);
        }

        chatMessageRepository.deleteAll(chatMessageRepository.findByRoomOrderByTimestampAsc(room));
        chatRoomUserService.getMembers(room).forEach(user -> chatRoomUserService.removeUserFromRoom(room, user.getUserId()));
        chatRoomRepository.delete(room);

        return new ApiResponse<>(200, "Room deleted successfully", "Room with code " + roomCode + " deleted.");
    }

    public ApiResponse<String> joinRoomResponse(String roomCode, String userId) {
        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        chatRoomUserService.addUserToRoom(optionalRoom.get(), userId, Role.MEMBER);
        return new ApiResponse<>(200, "User added to room successfully","User " + userId + " added to room " + roomCode);
    }

    public ApiResponse<String> removeMember(RoomMemberAction requestDTO) {

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> OptionalRequestor = chatRoomUserService.getUserInRoom(room, requestDTO.getUserId());
        if (OptionalRequestor.isEmpty()) return new ApiResponse<>(403, "You are not a member of this room", null);

        ChatRoomUser requester = OptionalRequestor.get();
        Role requesterRole = requester.getRole();

        Optional<ChatRoomUser> OptionalTarget = chatRoomUserService.getUserInRoom(room, requestDTO.getTargetUserId());
        if (OptionalTarget.isEmpty()) return new ApiResponse<>(404, "Target user not found in this room", null);

        ChatRoomUser target = OptionalTarget.get();
        Role targetRole = target.getRole();

        if (requestDTO.getUserId().equals(requestDTO.getTargetUserId())) {
            return new ApiResponse<>(400, "You cannot remove yourself from the room", null);
        }

        if (requesterRole == Role.ADMIN) {
            chatRoomUserService.removeUserFromRoom(room, requestDTO.getTargetUserId());
            return new ApiResponse<>(200, "Member removed successfully",
                    "User " + target.getUserId() + " removed from room " + room.getRoomCode());
        }

        if (requesterRole == Role.WORKSPACE_OWNER) {
            if (targetRole == Role.ADMIN || targetRole == Role.WORKSPACE_OWNER) {
                return new ApiResponse<>(403, "Workspace owner cannot remove Admin or another Workspace Owner", null);
            }
            chatRoomUserService.removeUserFromRoom(room, requestDTO.getTargetUserId());
            return new ApiResponse<>(200, "Member removed successfully","User " + target.getUserId() + " removed from room " + room.getRoomCode());
        }

        return new ApiResponse<>(403, "You are not authorized to remove members", null);
    }

    public ApiResponse<String> changeRole(RoleChangeAction requestDTO) {

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> OptionalRequestor = chatRoomUserService.getUserInRoom(room, requestDTO.getRequesterId());
        if (OptionalRequestor.isEmpty()) return new ApiResponse<>(403, "You are not a member of this room", null);

        ChatRoomUser requester = OptionalRequestor.get();
        Role requesterRole = requester.getRole();

        Optional<ChatRoomUser> OptionalTarget = chatRoomUserService.getUserInRoom(room, requestDTO.getTargetUserId());
        if (OptionalTarget.isEmpty()) return new ApiResponse<>(404, "Target user not found in this room", null);

        ChatRoomUser target = OptionalTarget.get();
        Role targetRole = target.getRole();

        if (requestDTO.getRequesterId().equals(requestDTO.getTargetUserId())) {
            return new ApiResponse<>(400, "You cannot change your own role", null);
        }

        if (requesterRole == Role.ADMIN) {
            target.setRole(requestDTO.getNewRole());
            chatRoomUserService.saveUser(target);
            return new ApiResponse<>(200, "Role updated successfully","User " + target.getUserId() + " is now " + target.getRole());
        }

        if (requesterRole == Role.WORKSPACE_OWNER) {
            if (targetRole == Role.ADMIN || targetRole == Role.WORKSPACE_OWNER) {
                return new ApiResponse<>(403, "Workspace owner cannot change the role of Admin or another Workspace Owner", null);
            }
            target.setRole(requestDTO.getNewRole());
            chatRoomUserService.saveUser(target);
            return new ApiResponse<>(200, "Role updated successfully","User " + target.getUserId() + " is now " + target.getRole());
        }

        return new ApiResponse<>(403, "You are not authorized to change roles", null);
    }

}
