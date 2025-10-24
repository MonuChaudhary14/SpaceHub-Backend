package org.spacehub.service.ChatRoom;

import org.spacehub.DTO.chatroom.*;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoom.ChatMessageRepository;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.commnunity.CommunityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomUserService chatRoomUserService;
    private final CommunityRepository communityRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           ChatMessageRepository chatMessageRepository,
                           ChatRoomUserService chatRoomUserService,
                           CommunityRepository communityRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomUserService = chatRoomUserService;
        this.communityRepository = communityRepository;
    }

    public ApiResponse<RoomResponseDTO> createRoom(CreateRoomRequest requestDTO) {

        if (requestDTO.getRoomName() == null || requestDTO.getRoomName().isBlank()) {
            return new ApiResponse<>(400, "Room name cannot be blank", null);
        }

        Community community = communityRepository.findById(requestDTO.getCommunityId()).orElseThrow(() -> new RuntimeException("Community not found"));

        boolean room_exists = chatRoomRepository.existsByRoomNameAndCommunityId(requestDTO.getRoomName(), requestDTO.getCommunityId());
        if (room_exists) {
            return new ApiResponse<>(400, "Room with this name already exists in the community", null);
        }

        ChatRoom room = ChatRoom.builder().roomName(requestDTO.getRoomName()).roomCode(UUID.randomUUID().toString())
                .createdBy(requestDTO.getCreatedBy()).community(community)
                .build();
        chatRoomRepository.save(room);
        chatRoomUserService.addUserToRoom(room, requestDTO.getCreatedBy(), Role.ADMIN);
        return new ApiResponse<>(200, "Room created successfully", RoomResponseDTO.fromEntity(room));
    }

    public ApiResponse<ChatRoom> getRoomByCodeData(String roomCode) {

        if (roomCode == null || roomCode.isBlank()) {
            return new ApiResponse<>(400, "Room code cannot be blank", null);
        }

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        return optionalRoom.map(room -> new ApiResponse<>(200, "Room fetched successfully", room))
                .orElseGet(() -> new ApiResponse<>(404, "Room not found", null));
    }

    public ApiResponse<List<ChatRoom>> getAllRoomsData() {
        List<ChatRoom> rooms = chatRoomRepository.findAll();
        return new ApiResponse<>(200, "Fetched all rooms", rooms);
    }

    @Transactional
    public ApiResponse<String> deleteRoomResponse(String roomCode, String userId) {

        if (roomCode == null || roomCode.isBlank()) return new ApiResponse<>(400, "Room code cannot be blank", null);

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> optionalUser = chatRoomUserService.getUserInRoom(room, userId);
        if (optionalUser.isEmpty()) return new ApiResponse<>(403, "You are not a member of this room", null);

        Role role = optionalUser.get().getRole();

        if (role != Role.ADMIN && role != Role.WORKSPACE_OWNER)
            return new ApiResponse<>(403, "You are not authorized to delete this room", null);

        chatMessageRepository.deleteAll(chatMessageRepository.findByRoomOrderByTimestampAsc(room));

        List<ChatRoomUser> members = chatRoomUserService.getMembers(room);
        for (ChatRoomUser member : members) {
            chatRoomUserService.removeUserFromRoom(room, member.getUserId());
        }
        chatRoomRepository.delete(room);

        return new ApiResponse<>(200, "Room deleted successfully", "Room with code " + roomCode + " deleted.");
    }

    public ApiResponse<String> joinRoomResponse(String roomCode, String userId) {

        if (roomCode == null || roomCode.isBlank()) return new ApiResponse<>(400, "Room code cannot be blank", null);
        if (userId == null || userId.isBlank()) return new ApiResponse<>(400, "User ID cannot be blank", null);

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

        ChatRoom room = optionalRoom.get();

        Community community = room.getCommunity();
        Optional<CommunityUser> communityUserOptional = community.getCommunityUsers().stream()
                .filter(communityUser -> communityUser.getUser().getId().equals(userId.trim())).findFirst();

        if (communityUserOptional.isEmpty()) {
            return new ApiResponse<>(403, "You are not a member of this community", null);
        }

        if (communityUserOptional.get().isBanned()) {
            return new ApiResponse<>(403, "You are blocked in this community and cannot join rooms", null);
        }

        Optional<ChatRoomUser> existingUser = chatRoomUserService.getUserInRoom(room, userId);
        if (existingUser.isPresent()) {
            return new ApiResponse<>(400, "User already in the room", null);
        }

        chatRoomUserService.addUserToRoom(room, userId, Role.MEMBER);
        return new ApiResponse<>(200, "User added to room successfully", "User " + userId + " added to room " + roomCode);
    }

    public ApiResponse<String> removeMember(RemoveMemberRequest requestDTO) {

        if (requestDTO.getRoomCode() == null || requestDTO.getRoomCode().isBlank())
            return new ApiResponse<>(400, "Room code cannot be blank", null);
        if (requestDTO.getRequesterId() == null || requestDTO.getRequesterId().isBlank())
            return new ApiResponse<>(400, "Requester ID cannot be blank", null);
        if (requestDTO.getTargetUserId() == null || requestDTO.getTargetUserId().isBlank())
            return new ApiResponse<>(400, "Target user ID cannot be blank", null);

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

        if (requestDTO.getRoomCode() == null || requestDTO.getRoomCode().isBlank())
            return new ApiResponse<>(400, "Room code cannot be blank", null);
        if (requestDTO.getRequesterId() == null || requestDTO.getRequesterId().isBlank())
            return new ApiResponse<>(400, "Requester ID cannot be blank", null);
        if (requestDTO.getTargetUserId() == null || requestDTO.getTargetUserId().isBlank())
            return new ApiResponse<>(400, "Target user ID cannot be blank", null);
        if (requestDTO.getNewRole() == null)
            return new ApiResponse<>(400, "New role cannot be null", null);

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
        if (roomCode == null || roomCode.isBlank()) return Optional.empty();
        return chatRoomRepository.findByRoomCode(roomCode.trim());
    }

    public ApiResponse<String> leaveRoom(LeaveRoomRequest requestDTO) {

        if (requestDTO.getRoomCode() == null || requestDTO.getRoomCode().isBlank())
            return new ApiResponse<>(400, "Room code cannot be blank", null);
        if (requestDTO.getUserId() == null || requestDTO.getUserId().isBlank())
            return new ApiResponse<>(400, "User ID cannot be blank", null);

        Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isEmpty()) {
            return new ApiResponse<>(404, "Room not found", null);
        }

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> optionalUser = chatRoomUserService.getUserInRoom(room, requestDTO.getUserId());
        if (optionalUser.isEmpty()) {
            return new ApiResponse<>(400, "You are not a member of this room", null);
        }

        ChatRoomUser leavingUser = optionalUser.get();
        if (leavingUser.getRole() == Role.ADMIN) {
            long adminCount = chatRoomUserService.getMembers(room).stream()
                    .filter(user -> user.getRole() == Role.ADMIN).count();
            if (adminCount <= 1) {
                return new ApiResponse<>(403, "Cannot leave room as the only ADMIN. Assign another ADMIN first.", null);
            }
        }

        chatRoomUserService.removeUserFromRoom(room, requestDTO.getUserId());
        return new ApiResponse<>(200, "Left room successfully", "User " + requestDTO.getUserId() + " has left room " + room.getRoomCode());
    }

    public ApiResponse<List<ChatRoom>> getRoomsByCommunity(Long communityId) {

        if (communityId == null) return new ApiResponse<>(400, "Community ID cannot be null", null);

        Optional<Community> optionalCommunity = communityRepository.findById(communityId);
        if (optionalCommunity.isEmpty()) {
            return new ApiResponse<>(404, "Community not found", null);
        }

        List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

        if (rooms.isEmpty()) {
            return new ApiResponse<>(200, "No chat rooms found in this community", List.of());
        }

        return new ApiResponse<>(200, "Chat rooms fetched successfully", rooms);
    }
}
