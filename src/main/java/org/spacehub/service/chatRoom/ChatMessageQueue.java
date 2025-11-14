package org.spacehub.service.chatRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.handler.ChatWebSocketHandler;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatMessageQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatMessageQueue implements IChatMessageQueue {

  private final List<ChatMessage> queue = new ArrayList<>();
  private final ChatMessageService chatMessageService;
  private ChatWebSocketHandler chatWebSocketHandler;

  @Autowired
  @Lazy
  public void setChatWebSocketHandler(ChatWebSocketHandler handler) {
    this.chatWebSocketHandler = handler;
  }

  public synchronized void enqueue(ChatMessage message) {
    queue.add(message);
    if (queue.size() >= 10) {
      flushQueue();
    }
  }

  @Scheduled(fixedRate = 10000)
  public synchronized void flushQueue() {
    if (queue.isEmpty()) {
      return;
    }

    List<ChatMessage> batch = new ArrayList<>(queue);
    queue.clear();

    List<ChatMessage> saved = chatMessageService.saveAll(batch);

    if (chatWebSocketHandler != null) {
      for (ChatMessage message : saved) {
        try {
          chatWebSocketHandler.broadcastMessageToRoom(message);
        } catch (Exception ignored) {}
      }
    }
  }

  public synchronized boolean deleteMessageByUuid(String messageUuid) {
    queue.removeIf(m -> Objects.equals(m.getMessageUuid(), messageUuid));
    return chatMessageService.deleteMessageByUuid(messageUuid);
  }

  public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
    return chatMessageService.getMessagesForRoom(room);
  }

  public List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom) {
    return chatMessageService.getMessagesForNewChatRoom(newChatRoom);
  }
}
