package org.spacehub.service.VoiceRoom;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.repository.voiceRoom.VoiceRoomRepository;
import org.spacehub.service.Interface.IVoiceRoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.spacehub.utils.SecurityUtils;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class VoiceRoomService implements IVoiceRoomService {

  private static final Logger logger = LoggerFactory.getLogger(VoiceRoomService.class);

  private final VoiceRoomRepository voiceRoomRepository;

  @Transactional
  public VoiceRoom createVoiceRoom(ChatRoom chatRoom, String name) {
    String createdBy = SecurityUtils.getCurrentUserEmail();
    voiceRoomRepository.findByNameAndChatRoom(name, chatRoom).ifPresent(r -> {
      throw new IllegalStateException("Voice room '" + name + "' already exists in this group");
    });

    int roomIdNumeric = 1000 + new Random().nextInt(900000);
    String roomCode = "vr_" + (chatRoom != null ? chatRoom.getId() : "global") + "_" + System.currentTimeMillis();

    VoiceRoom voiceRoom = VoiceRoom.builder()
      .janusRoomId(roomIdNumeric)
      .name(name)
      .createdBy(createdBy)
      .chatRoom(chatRoom)
      .active(true)
      .roomCode(roomCode)
      .build();

    voiceRoomRepository.save(voiceRoom);

    logger.info("Created LiveKit voice/video room '{}' (roomCode={}) for chatRoom '{}'",
      name, roomCode, chatRoom != null ? chatRoom.getName() : "none");

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
      .orElseThrow(() -> new RuntimeException("Voice room not found: " + roomName));

    if (!room.getCreatedBy().equals(requester)) {
      throw new RuntimeException("Only the creator can delete this voice room");
    }

    voiceRoomRepository.delete(room);
    logger.info("Deleted voice room '{}' from chatRoom '{}'", roomName, chatRoom.getName());
  }

}
