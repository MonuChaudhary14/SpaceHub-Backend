package org.spacehub.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatRoomUserService {

    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    public void addUserToRoom(String roomCode, String userId) {
        roomUsers.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public void removeUserFromRoom(String roomCode, String userId) {
        Set<String> users = roomUsers.get(roomCode);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                roomUsers.remove(roomCode);
            }
        }
    }

}
