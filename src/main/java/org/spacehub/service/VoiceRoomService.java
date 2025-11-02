package org.spacehub.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VoiceRoomService {

    private final Map<String, Set<String>> roomParticipants = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> roomMuteStates = new ConcurrentHashMap<>();

    public void addUserToRoom(String roomId, String username) {
        roomParticipants.computeIfAbsent(roomId, r -> ConcurrentHashMap.newKeySet()).add(username);
        roomMuteStates.computeIfAbsent(roomId, r -> new ConcurrentHashMap<>()).put(username, false);
        System.out.println("User " + username + " joined room " + roomId);
    }

    public void removeUserFromRoom(String roomId, String username) {
        Set<String> participants = roomParticipants.get(roomId);
        if (participants != null) {
            participants.remove(username);
            if (participants.isEmpty()) {
                roomParticipants.remove(roomId);
                roomMuteStates.remove(roomId);
            }
        }
        System.out.println("User " + username + " left room " + roomId);
    }

    public void updateUserMuteState(String roomId, String username, boolean muted) {
        roomMuteStates.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(username, muted);
        System.out.println("[VoiceRoomService] User " + username + " mute=" + muted + " in room " + roomId);
    }

    public Set<String> getParticipants(String roomId) {
        return roomParticipants.getOrDefault(roomId, Collections.emptySet());
    }

    public Map<String, Boolean> getMuteStates(String roomId) {
        return roomMuteStates.getOrDefault(roomId, Collections.emptyMap());
    }

    public void clearAllRooms() {
        roomParticipants.clear();
        roomMuteStates.clear();
        System.out.println("Cleared all rooms.");
    }

}
