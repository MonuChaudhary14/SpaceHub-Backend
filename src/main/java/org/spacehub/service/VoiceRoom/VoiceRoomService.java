package org.spacehub.service.VoiceRoom;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.repository.voiceRoom.VoiceRoomRepository;
import org.spacehub.service.Interface.IVoiceRoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.spacehub.utils.SecurityUtils;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoiceRoomService implements IVoiceRoomService {

  private static final Logger logger = LoggerFactory.getLogger(VoiceRoomService.class);

  private final VoiceRoomRepository voiceRoomRepository;
  private final UserRepository userRepository;
  private final CommunityUserRepository communityUserRepository;

  @Transactional
  public VoiceRoom createVoiceRoom(ChatRoom chatRoom, String name) {
    return createVoiceRoom(chatRoom, name, "VOICE");
  }

  @Transactional
  public VoiceRoom createVoiceRoom(ChatRoom chatRoom, String name, String roomType) {
    String createdBy = SecurityUtils.getCurrentUserEmail();
    voiceRoomRepository.findByNameAndChatRoom(name, chatRoom).ifPresent(r -> {
      throw new IllegalStateException("Room '" + name + "' already exists in this group");
    });

    String normalizedType = "VIDEO".equalsIgnoreCase(roomType) ? "VIDEO" : "VOICE";
    String prefix = "VIDEO".equals(normalizedType) ? "vid_" : "vr_";
    String roomCode = prefix + (chatRoom != null ? chatRoom.getId() : "global") + "_" + System.currentTimeMillis();

    VoiceRoom voiceRoom = VoiceRoom.builder()
      .name(name)
      .createdBy(createdBy)
      .chatRoom(chatRoom)
      .roomType(normalizedType)
      .active(true)
      .roomCode(roomCode)
      .build();

    voiceRoomRepository.save(voiceRoom);

    logger.info("Created LiveKit {} room '{}' (roomCode={}) for chatRoom '{}'",
      normalizedType, name, roomCode, chatRoom != null ? chatRoom.getName() : "none");

    return voiceRoom;
  }

  @Transactional(readOnly = true)
  public List<VoiceRoom> getVoiceRoomsForChatRoom(ChatRoom chatRoom) {
    return voiceRoomRepository.findByChatRoom(chatRoom);
  }

  @Transactional
  public void deleteVoiceRoom(ChatRoom chatRoom, String roomName) {
    String requester = SecurityUtils.getCurrentUserEmail();
    VoiceRoom room = voiceRoomRepository.findByNameAndChatRoom(roomName, chatRoom)
      .orElseThrow(() -> new RuntimeException("Media room not found: " + roomName));

    boolean isCreator = room.getCreatedBy() != null && room.getCreatedBy().equalsIgnoreCase(requester);
    boolean isAuthorized = isCreator;

    if (!isAuthorized && chatRoom != null && chatRoom.getCommunity() != null && requester != null) {
      Community community = chatRoom.getCommunity();
      User user = userRepository.findByEmail(requester).orElse(null);
      if (user != null) {
        CommunityUser cu = communityUserRepository.findByCommunityIdAndUserId(community.getId(), user.getId()).orElse(null);
        if (cu != null && (cu.getRole() == Role.ADMIN || cu.getRole() == Role.OWNER)) {
          isAuthorized = true;
        }
        if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(user.getId())) {
          isAuthorized = true;
        }
      }
    }

    if (!isAuthorized) {
      throw new RuntimeException("Only the creator, admin, or community owner can delete this room");
    }

    voiceRoomRepository.delete(room);
    logger.info("Deleted room '{}' from chatRoom '{}'", roomName, chatRoom != null ? chatRoom.getName() : "unknown");
  }

}
