package org.spacehub.service.community;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Community.CreateRoomRequest;
import org.spacehub.DTO.Community.RenameRoomRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityRoomService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final CommunityUserRepository communityUserRepository;

  public ResponseEntity<?> createRoomInCommunity(CreateRoomRequest request) {
    try {
      if (request == null || request.getCommunityId() == null) {
        return badRequest("Community ID is required");
      }

      Community community = communityRepository.findById(request.getCommunityId())
        .orElseThrow(() -> new RuntimeException("Community not found with ID: " + request.getCommunityId()));
      User requester = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("Requester not found"));

      CommunityUser communityUser = communityUserRepository.findByCommunityIdAndUserId(community.getId(), requester.getId())
        .orElse(null);
      if (communityUser == null) {
        return forbidden("You are not a member of this community");
      }

      if (!canCreateRoom(community, requester, communityUser.getRole())) {
        return forbidden("Only owners or admins can create rooms");
      }

      if (request.getRoomName() == null || request.getRoomName().isBlank()) {
        return badRequest("Room name cannot be empty");
      }

      boolean exists = chatRoomRepository.findByCommunityId(community.getId()).stream()
        .anyMatch(room -> room.getName().equalsIgnoreCase(request.getRoomName().trim()));

      if (exists) {
        return badRequest("A room with this name already exists");
      }

      ChatRoom room = new ChatRoom();
      room.setName(request.getRoomName().trim());
      room.setCommunity(community);
      room.setRoomCode(UUID.randomUUID());
      ChatRoom savedRoom = chatRoomRepository.save(room);

      return ResponseEntity.status(201)
        .body(new ApiResponse<>(201, "Room created successfully", savedRoom));

    } catch (RuntimeException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return serverError("An unexpected error occurred: " + e.getMessage());
    }
  }

  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoomsByCommunity(UUID communityId) {
    try {
      if (communityId == null) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community ID is required", null));
      }

      Optional<Community> optionalCommunity = communityRepository.findById(communityId);
      if (optionalCommunity.isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
      }

      List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

      List<Map<String, Object>> out = rooms.stream()
        .map(r -> {
          Map<String, Object> m = new HashMap<>();
          m.put("id", r.getId());
          m.put("name", r.getName());
          m.put("roomCode", r.getRoomCode());
          return m;
        }).collect(Collectors.toList());

      return ResponseEntity.ok(new ApiResponse<>(200, "Rooms fetched successfully", out));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithRooms(UUID communityId) {
    try {
      Optional<Community> optionalCommunity = communityRepository.findById(communityId);
      if (optionalCommunity.isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
      }

      Community community = optionalCommunity.get();
      User user = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("User not found"));

      boolean isMember = communityUserRepository.findByCommunityIdAndUserId(community.getId(), user.getId()).isPresent();
      if (!isMember) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "Access denied: You are not a member of this community", null));
      }

      List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);
      Map<String, Object> response = new HashMap<>();
      response.put("communityId", community.getId());
      response.put("communityName", community.getName());
      response.put("description", community.getDescription());
      response.put("rooms", rooms);

      List<Map<String, Object>> members = new ArrayList<>();
      for (CommunityUser communityUser : community.getCommunityUsers()) {
        Map<String, Object> memberData = new HashMap<>();
        User u = communityUser.getUser();
        memberData.put("email", u != null ? u.getEmail() : null);
        memberData.put("username", u != null ? u.getUsername() : null);
        memberData.put("role", communityUser.getRole() != null ? communityUser.getRole().toString() : "MEMBER");
        members.add(memberData);
      }
      response.put("members", members);

      return ResponseEntity.ok(new ApiResponse<>(200, "Community details fetched successfully", response));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error", null));
    }
  }

  public ResponseEntity<?> deleteRoom(UUID communityId, UUID roomId) {
    try {
      String requesterEmail = SecurityUtils.getCurrentUserEmail();
      if (communityId == null || roomId == null || requesterEmail == null || requesterEmail.isBlank()) {
        return badRequest("Community ID, Room ID, and requester email are required");
      }

      ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new RuntimeException("Room not found with ID: " + roomId));
      Community community = room.getCommunity();
      if (community == null || !community.getId().equals(communityId)) {
        throw new RuntimeException("Room does not belong to the specified community");
      }

      User requester = userRepository.findByEmail(requesterEmail.trim().toLowerCase())
        .orElseThrow(() -> new RuntimeException("Requester not found with email: " + requesterEmail));

      CommunityUser communityUser = communityUserRepository.findByCommunityIdAndUserId(community.getId(), requester.getId())
        .orElseThrow(() -> new RuntimeException("You are not a member of this community"));

      Role role = communityUser.getRole();
      boolean canDelete = role == Role.ADMIN || role == Role.OWNER
        || (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId()));
      if (!canDelete) {
        return forbidden("Only owners or admins can delete rooms");
      }

      chatRoomRepository.delete(room);
      return ResponseEntity.ok(new ApiResponse<>(200, "Room deleted successfully", "Room deleted successfully"));
    } catch (RuntimeException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return serverError("Unexpected error: " + e.getMessage());
    }
  }

  public ResponseEntity<?> renameRoomInCommunity(UUID communityId, UUID roomId, RenameRoomRequest req) {
    if (communityId == null || roomId == null || req == null || req.getNewRoomName() == null || req.getNewRoomName().isBlank()) {
      return badRequest("Community ID, Room ID, and New room name are required");
    }

    try {
      Community community = communityRepository.findById(communityId)
        .orElseThrow(() -> new RuntimeException("Community not found"));
      User requester = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("User not found"));

      CommunityUser communityUser = communityUserRepository
        .findByCommunityIdAndUserId(community.getId(), requester.getId())
        .orElse(null);
      if (communityUser == null) {
        return forbidden("You are not a member of this community");
      }

      if (!canCreateRoom(community, requester, communityUser.getRole())) {
        return forbidden("Only owners or admins can rename rooms");
      }

      ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new RuntimeException("Room not found"));

      if (room.getCommunity() == null || !room.getCommunity().getId().equals(communityId)) {
        return badRequest("Room doesn't belong to the specified community");
      }

      String newRoomName = req.getNewRoomName().trim();
      boolean nameExists = chatRoomRepository.findByCommunityId(communityId).stream()
        .anyMatch(r -> r.getName().equalsIgnoreCase(newRoomName) && !r.getId().equals(roomId));

      if (nameExists) {
        return badRequest("Another room with this name already exists");
      }

      room.setName(newRoomName);
      chatRoomRepository.save(room);

      return ResponseEntity.ok(
        new ApiResponse<>(200, "Room renamed successfully", Map.of(
          "roomId", room.getId(),
          "newName", room.getName(),
          "communityId", community.getId()
        ))
      );
    } catch (RuntimeException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return serverError("Unexpected error: " + e.getMessage());
    }
  }

  private boolean canCreateRoom(Community community, User requester, Role role) {
    return role == Role.ADMIN || role == Role.OWNER ||
      (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId()));
  }

  private ResponseEntity<ApiResponse<Object>> badRequest(String message) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(400, message, null));
  }

  private ResponseEntity<ApiResponse<Object>> forbidden(String message) {
    return ResponseEntity.status(403).body(new ApiResponse<>(403, message, null));
  }

  private ResponseEntity<ApiResponse<Object>> serverError(String message) {
    return ResponseEntity.internalServerError().body(new ApiResponse<>(500, message, null));
  }
}
