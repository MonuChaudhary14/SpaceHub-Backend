package org.spacehub.service.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatPoll;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatVote;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoom.ChatPollRepository;
import org.spacehub.repository.ChatRoom.ChatVoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatPollService {

    private final ChatPollRepository pollRepository;
    private final ChatVoteRepository voteRepository;
    private final ChatRoomUserService chatRoomUserService;
    private final ChatRoomService chatRoomService;

    public ChatPollService(ChatPollRepository pollRepository,
                           ChatVoteRepository voteRepository,
                           ChatRoomUserService chatRoomUserService,
                           ChatRoomService chatRoomService) {
        this.pollRepository = pollRepository;
        this.voteRepository = voteRepository;
        this.chatRoomUserService = chatRoomUserService;
        this.chatRoomService = chatRoomService;
    }

    public ChatPoll createPoll(String roomCode, String userId, Map<String, Object> body) {

        ChatRoom room = chatRoomService.findByRoomCode(roomCode).orElseThrow(() -> new RuntimeException("Room not found"));

        boolean checkAccess = chatRoomUserService.getMembers(room).stream().anyMatch(member -> member.getUserId().equals(userId) &&
                        (member.getRole() == Role.ADMIN || member.getRole() == Role.WORKSPACE_OWNER));

        if (!checkAccess) throw new RuntimeException("Permission denied");

        String question = (String) body.get("question");
        List<String> options = (List<String>) body.get("options");

        ChatPoll poll = ChatPoll.builder()
                .room(room)
                .question(question)
                .options(options)
                .timestamp(System.currentTimeMillis())
                .build();

        return pollRepository.save(poll);
    }

    public List<ChatPoll> getPollsForRoom(String roomCode) {
        ChatRoom room = chatRoomService.findByRoomCode(roomCode).orElseThrow(() -> new RuntimeException("Room not found"));
        return pollRepository.findByRoomOrderByTimestampAsc(room);
    }

    public ChatVote vote(String roomCode, String userId, Map<String, Object> body) {

        Long pollId = Long.valueOf(body.get("pollId").toString());
        int optionIndex = Integer.parseInt(body.get("optionIndex").toString());

        ChatPoll poll = pollRepository.findById(pollId).orElseThrow(() -> new RuntimeException("Poll not found"));

        boolean isMember = chatRoomUserService.getMembers(poll.getRoom()).stream().anyMatch(member -> member.getUserId().equals(userId));

        if (!isMember) throw new RuntimeException("User is not a member of the room");

        Optional<ChatVote> existingVote = voteRepository.findByPollAndUserId(poll, userId);
        ChatVote vote;

        if (existingVote.isPresent()) {
            vote = existingVote.get();
            vote.setOptionIndex(optionIndex);
        }
        else {
            vote = ChatVote.builder().poll(poll).userId(userId).optionIndex(optionIndex).build();
        }

        return voteRepository.save(vote);
    }

    public List<ChatVote> getVotes(Long pollId) {
        ChatPoll poll = pollRepository.findById(pollId).orElseThrow(() -> new RuntimeException("Poll not found"));
        return voteRepository.findByPoll(poll);
    }

}
