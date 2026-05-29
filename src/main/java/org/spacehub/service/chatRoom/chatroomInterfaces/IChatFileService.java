package org.spacehub.service.chatRoom.chatroomInterfaces;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IChatFileService {

  ChatMessage uploadChatFile(MultipartFile file, String roomCode) throws IOException;

  String getDownloadLink(String fileKeyOrUrl);

  void deleteFile(String fileUrlOrKey);

}
