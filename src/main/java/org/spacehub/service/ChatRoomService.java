package org.spacehub.service;

import org.spacehub.DTO.CreateRoomRequest;
import org.spacehub.DTO.chatroom.LeaveRoomRequest;
import org.spacehub.DTO.chatroom.RoleChangeAction;
import org.spacehub.DTO.chatroom.RoomMemberAction;
import org.spacehub.DTO.chatroom.RoomResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoom.ChatMessageRepository;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.service.Interface.IChatRoomService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Service
public class ChatRoomService implements IChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final org.spacehub.service.ChatRoomUserService chatRoomUserService;
  private final CommunityRepository communityRepository;

  public ChatRoomService(ChatRoomRepository chatRoomRepository,
                         ChatMessageRepository chatMessageRepository,
                         org.spacehub.service.ChatRoomUserService chatRoomUserService, CommunityRepository communityRepository) {
    this.chatRoomRepository = chatRoomRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.chatRoomUserService = chatRoomUserService;
    this.communityRepository = communityRepository;
  }

  @Cacheable(value = "chatRooms", key = "#roomCode")
  public Optional<ChatRoom> findByRoomCode(UUID roomCode) {
    return chatRoomRepository.findByRoomCode(roomCode);
  }

  @CacheEvict(value = {"chatRooms", "chatRoomData", "allRooms"}, allEntries = true)
  public ApiResponse<RoomResponseDTO> createRoom(CreateRoomRequest requestDTO) {
    ChatRoom room = ChatRoom.builder()
            .name(requestDTO.getName())
            .roomCode(UUID.randomUUID())
            .build();

    chatRoomRepository.save(room);
    chatRoomUserService.addUserToRoom(room, requestDTO.getEmail(), Role.ADMIN);

    RoomResponseDTO responseDTO = RoomResponseDTO.builder()
            .roomCode(String.valueOf(room.getRoomCode()))
            .name(room.getName())
            .message("Room created successfully")
            .build();

    return new ApiResponse<>(200, "Room created successfully", responseDTO);
  }

  @Cacheable(value = "chatRoomData", key = "#roomCode")
  public ApiResponse<ChatRoom> getRoomByCodeData(String roomCode) {
    Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
    return optionalRoom
            .map(chatRoom -> new ApiResponse<>(200, "Room fetched successfully", chatRoom))
            .orElseGet(() -> new ApiResponse<>(404, "Room not found", null));
  }

  @Cacheable(value = "allRooms")
  public ApiResponse<List<ChatRoom>> getAllRoomsData() {
    List<ChatRoom> rooms = chatRoomRepository.findAll();
    return new ApiResponse<>(200, "Fetched all rooms", rooms);
  }

  @CacheEvict(value = {"chatRooms", "chatRoomData", "allRooms"}, allEntries = true)
  public ApiResponse<String> deleteRoomResponse(String roomCode, String requesterEmail) {
    Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
    if (optionalRoom.isEmpty()) return new ApiResponse<>(403, "Room not found", null);

    ChatRoom room = optionalRoom.get();

    Optional<ChatRoomUser> userOpt = chatRoomUserService.getUserInRoom(room, requesterEmail);
    if (userOpt.isEmpty())
      return new ApiResponse<>(403, "You are not a member of this room", null);

    Role role = userOpt.get().getRole();
    if (role != Role.ADMIN && role != Role.WORKSPACE_OWNER)
      return new ApiResponse<>(403, "You are not authorized to delete this room", null);

    chatMessageRepository.deleteAll(chatMessageRepository.findByRoomOrderByTimestampAsc(room));
    chatRoomUserService.getMembers(room)
            .forEach(user -> chatRoomUserService.removeUserFromRoom(room, user.getEmail()));

    chatRoomRepository.delete(room);

    return new ApiResponse<>(200, "Room deleted successfully",
            "Room with code " + roomCode + " deleted.");
  }

  @CacheEvict(value = {"chatRooms", "chatRoomData", "allRooms"}, allEntries = true)
  public ApiResponse<String> joinRoomResponse(String roomCode, String email) {
    Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
    if (optionalRoom.isEmpty()) {
      return new ApiResponse<>(404, "Room not found", null);
    }

    ChatRoom room = optionalRoom.get();
    Optional<ChatRoomUser> userOpt = chatRoomUserService.getUserInRoom(room, email);
    if (userOpt.isPresent()) {
      return new ApiResponse<>(400, "User is already in this room", null);
    }

    chatRoomUserService.addUserToRoom(room, email, Role.MEMBER);
    return new ApiResponse<>(200, "User added to room successfully",
            "User " + email + " added to room " + roomCode);
  }

  @CacheEvict(value = {"chatRooms", "chatRoomData", "allRooms"}, allEntries = true)
  public ApiResponse<String> removeMember(RoomMemberAction requestDTO) {
    Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
    if (optionalRoom.isEmpty()) {
      return new ApiResponse<>(404, "Room not found", null);
    }

    ChatRoom room = optionalRoom.get();
    ApiResponse<RoleContext> ctxResponse = getRoleContext(room, requestDTO.getEmail(), requestDTO.getTargetEmail());

    if (ctxResponse.getStatus() != 200) {
      return new ApiResponse<>(ctxResponse.getStatus(), ctxResponse.getMessage(), null);
    }

    RoleContext ctx = ctxResponse.getData();

    if (requestDTO.getEmail().equals(requestDTO.getTargetEmail())) {
      return new ApiResponse<>(400, "You cannot remove yourself. Please use the 'leave room' API.", null);
    }

    assert ctx != null;
    if (ctx.requesterRole == Role.WORKSPACE_OWNER) {
      if (ctx.targetRole == Role.WORKSPACE_OWNER) {
        return new ApiResponse<>(403, "A Workspace Owner cannot remove another Workspace Owner.", null);
      }
      chatRoomUserService.removeUserFromRoom(room, requestDTO.getTargetEmail());
      return new ApiResponse<>(200, "Member removed successfully",
              "User " + ctx.target.getEmail() + " removed from room " + room.getRoomCode());
    }

    if (ctx.requesterRole == Role.ADMIN) {
      if (ctx.targetRole == Role.WORKSPACE_OWNER || ctx.targetRole == Role.ADMIN) {
        return new ApiResponse<>(403, "An Admin cannot remove another Admin or a Workspace Owner.", null);
      }
      chatRoomUserService.removeUserFromRoom(room, requestDTO.getTargetEmail());
      return new ApiResponse<>(200, "Member removed successfully",
              "User " + ctx.target.getEmail() + " removed from room " + room.getRoomCode());
    }

    return new ApiResponse<>(403, "You are not authorized to remove members", null);
  }

  @CacheEvict(value = {"chatRooms", "chatRoomData", "allRooms"}, allEntries = true)
  public ApiResponse<String> changeRole(RoleChangeAction requestDTO) {
    Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
    if (optionalRoom.isEmpty()) return new ApiResponse<>(404, "Room not found", null);

    ChatRoom room = optionalRoom.get();

    ApiResponse<RoleContext> ctxResponse = getRoleContext(room, requestDTO.getRequesterEmail(), requestDTO.getTargetEmail());
    if (ctxResponse.getStatus() != 200)
      return new ApiResponse<>(ctxResponse.getStatus(), ctxResponse.getMessage(), null);

    RoleContext ctx = ctxResponse.getData();

    if (requestDTO.getRequesterEmail().equals(requestDTO.getTargetEmail())) {
      return new ApiResponse<>(400, "You cannot change your own role", null);
    }

    assert ctx != null;
    if (ctx.requesterRole == Role.ADMIN) {
      ctx.target.setRole(requestDTO.getNewRole());
      chatRoomUserService.saveUser(ctx.target);
      return new ApiResponse<>(200, "Role updated successfully, User " + ctx.target.getEmail()
              + " is now " + ctx.target.getRole());
    }

    if (ctx.requesterRole == Role.WORKSPACE_OWNER) {
      if (ctx.targetRole == Role.ADMIN || ctx.targetRole == Role.WORKSPACE_OWNER) {
        return new ApiResponse<>(403,
                "Workspace owner cannot change the role of Admin or another Workspace Owner", null);
      }
      ctx.target.setRole(requestDTO.getNewRole());
      chatRoomUserService.saveUser(ctx.target);
      return new ApiResponse<>(200, "Role updated successfully, User " + ctx.target.getEmail()
              + " is now " + ctx.target.getRole());
    }

    return new ApiResponse<>(403, "You are not authorized to change roles", null);
  }

  private ApiResponse<RoleContext> getRoleContext(ChatRoom room, String requesterEmail, String targetEmail) {
    Optional<ChatRoomUser> reqOpt = chatRoomUserService.getUserInRoom(room, requesterEmail);
    if (reqOpt.isEmpty())
      return new ApiResponse<>(403, "You are not a member of this room", null);

    Optional<ChatRoomUser> tgtOpt = chatRoomUserService.getUserInRoom(room, targetEmail);
    return tgtOpt.map(chatRoomUser ->
                    new ApiResponse<>(200, "Fetched successfully", new RoleContext(reqOpt.get(), chatRoomUser)))
            .orElseGet(() -> new ApiResponse<>(404, "Target user not found in this room", null));
  }

  public ApiResponse<String> leaveRoom(LeaveRoomRequest requestDTO) {

    Optional<ChatRoom> optionalRoom = chatRoomRepository.findByRoomCode(requestDTO.getRoomCode());
    if (optionalRoom.isEmpty()) {
      return new ApiResponse<>(404, "Room not found", null);
    }

    ChatRoom room = optionalRoom.get();

    Optional<ChatRoomUser> optionalUser = chatRoomUserService.getUserInRoom(room, String.valueOf(requestDTO.getEmail()));
    if (optionalUser.isEmpty()) {
      return new ApiResponse<>(400, "You are not a member of this room", null);
    }

    chatRoomUserService.removeUserFromRoom(room, String.valueOf(requestDTO.getEmail()));
    return new ApiResponse<>(200, "Left room successfully", "User " + requestDTO.getEmail()
      + " has left room " + room.getRoomCode());
  }

  public ApiResponse<List<ChatRoom>> getRoomsByCommunity(UUID communityId) {

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

  private static class RoleContext {
    ChatRoomUser requester;
    ChatRoomUser target;
    Role requesterRole;
    Role targetRole;

    RoleContext(ChatRoomUser requester, ChatRoomUser target) {
      this.requester = requester;
      this.target = target;
      this.requesterRole = requester.getRole();
      this.targetRole = target.getRole();
    }
  }
}
