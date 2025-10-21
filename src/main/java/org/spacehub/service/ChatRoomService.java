package org.spacehub.service;

import org.spacehub.DTO.RoomMemberAction;
import org.spacehub.entities.ApiResponse;
import org.spacehub.entities.ChatRoom;
import org.spacehub.entities.ChatRoomUser;
import org.spacehub.entities.Role;
import org.spacehub.repository.ChatMessageRepository;
import org.spacehub.repository.ChatRoomRepository;
import org.spacehub.repository.ChatRoomUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatRoomUserService chatRoomUserService;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           ChatMessageRepository chatMessageRepository,
                           ChatRoomUserRepository chatRoomUserRepository,
                           ChatRoomUserService chatRoomUserService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomUserRepository = chatRoomUserRepository;
        this.chatRoomUserService = chatRoomUserService;
    }

    public ChatRoom createRoom(String name){
        ChatRoom room = ChatRoom.builder().name(name).roomCode(UUID.randomUUID().toString()).build();
        return chatRoomRepository.save(room);
    }

    public Optional<ChatRoom> findByRoomCode(String roomCode) {
        return chatRoomRepository.findByRoomCode(roomCode);
    }

    public List<ChatRoom> getAllRooms() {
        return chatRoomRepository.findAll();
    }

    public boolean deleteRoom(String roomCode, String userId) {
        Optional<ChatRoom> OptionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (OptionalRoom.isEmpty()) return false;

        ChatRoom room = OptionalRoom.get();

        Optional<ChatRoomUser> userRoomOpt = chatRoomUserRepository.findByRoomAndUserId(room, userId);

        if (userRoomOpt.isEmpty()) return false;

        Role role = userRoomOpt.get().getRole();
        if (role != Role.ADMIN && role != Role.WORKSPACE_OWNER) {
            return false;
        }

        chatMessageRepository.deleteAll(chatMessageRepository.findByRoomOrderByTimestampAsc(room));
        chatRoomUserRepository.deleteByRoom(room);
        chatRoomRepository.delete(room);

        return true;
    }

    public ApiResponse<String> removeMember(RoomMemberAction requestDTO) {

        Optional<ChatRoom> optionalRoom = findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isEmpty()) {
            return new ApiResponse<>(404, "Room not found", null);
        }

        ChatRoom room = optionalRoom.get();

        Optional<ChatRoomUser> OptionalRequester = chatRoomUserService.getUserInRoom(room, requestDTO.getUserId());
        if (OptionalRequester.isEmpty()) {
            return new ApiResponse<>(403, "You are not a member of this room", null);
        }

        ChatRoomUser requester = OptionalRequester.get();
        Role requesterRole = requester.getRole();

        Optional<ChatRoomUser> OptionalTarget = chatRoomUserService.getUserInRoom(room, requestDTO.getTargetUserId());
        if (OptionalTarget.isEmpty()) {
            return new ApiResponse<>(404, "Target user not found in this room", null);
        }

        ChatRoomUser targetUser = OptionalTarget.get();
        Role targetRole = targetUser.getRole();

        if (requestDTO.getUserId().equals(requestDTO.getTargetUserId())) {
            return new ApiResponse<>(400, "You cannot remove yourself from the room", null);
        }

        if (requesterRole == Role.WORKSPACE_OWNER) {

            if (targetRole == Role.ADMIN) {
                return new ApiResponse<>(403, "Workspace owner cannot remove an admin", null);
            }

            chatRoomUserService.removeUserFromRoom(room, requestDTO.getTargetUserId());
            return new ApiResponse<>(200, "Member removed successfully","User " + requestDTO.getTargetUserId() + " removed from room " + room.getRoomCode());
        }

        if (requesterRole == Role.ADMIN) {
            chatRoomUserService.removeUserFromRoom(room, requestDTO.getTargetUserId());
            return new ApiResponse<>(200, "Member removed successfully","User " + requestDTO.getTargetUserId() + " removed from room " + room.getRoomCode());
        }

        return new ApiResponse<>(403, "You are not authorized to remove members", null);
    }


}
