package org.spacehub.service.chatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.handler.ChatWebSocketHandler;
import org.spacehub.service.Interface.IS3Service;
import org.spacehub.service.S3Service;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatMessageQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@Service
public class ChatMessageQueue implements IChatMessageQueue {

  private final List<ChatMessage> queue = new ArrayList<>();
  private final ChatMessageService chatMessageService;
  private final S3Service s3Service;
  private ChatWebSocketHandler chatWebSocketHandler;

  public ChatMessageQueue(ChatMessageService chatMessageService, S3Service s3Service) {
    this.chatMessageService = chatMessageService;
    this.s3Service = s3Service;
  }

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
    if (queue.isEmpty()) return;

    List<ChatMessage> batch = new ArrayList<>(queue);
    queue.clear();

    List<ChatMessage> saved = chatMessageService.saveAll(batch);

    if (chatWebSocketHandler != null) {
      for (ChatMessage message : saved) {
        try {
          chatWebSocketHandler.broadcastMessageToRoom(message);
        }
        catch (Exception ignored) {

        }
      }
    }
  }

  public synchronized boolean deleteMessage(Long messageId) {

    queue.removeIf(m -> Objects.equals(m.getId(), messageId));
    return chatMessageService.deleteMessageById(messageId);
  }

  public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
    return chatMessageService.getMessagesForRoom(room);
  }

  public List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom) {
    return chatMessageService.getMessagesForNewChatRoom(newChatRoom);
  }
}
