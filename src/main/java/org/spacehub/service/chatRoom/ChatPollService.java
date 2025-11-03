package org.spacehub.service.chatRoom;

import org.spacehub.entities.ChatRoom.ChatPoll;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatVote;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoom.ChatPollRepository;
import org.spacehub.repository.ChatRoom.ChatVoteRepository;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatPollService;
import org.spacehub.service.ChatRoomService;
import org.spacehub.service.ChatRoomUserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatPollService implements IChatPollService {

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

  public ChatPoll createPoll(String roomCode, String email , Map<String, Object> body) {

    ChatRoom room = chatRoomService.findByRoomCode(UUID.fromString(roomCode)).orElseThrow(() -> new RuntimeException("Room not found"));

    boolean checkAccess = chatRoomUserService.getMembers(room).stream().anyMatch(member -> member.getEmail().equals(email) &&
                    (member.getRole() == Role.ADMIN || member.getRole() == Role.WORKSPACE_OWNER));

    if (!checkAccess) throw new RuntimeException("Permission denied");

    String question = (String) body.get("question");
    Object optionsObj = body.get("options");
    List<String> options = new ArrayList<>();

    if (optionsObj instanceof List<?>) {
      for (Object obj : (List<?>) optionsObj) {
        if (obj instanceof String) {
          options.add((String) obj);
        }
      }
    }


    ChatPoll poll = ChatPoll.builder()
            .room(room)
            .question(question)
            .options(options)
            .timestamp(System.currentTimeMillis())
            .build();

    return pollRepository.save(poll);
  }

  public List<ChatPoll> getPollsForRoom(String roomCode) {
    ChatRoom room = chatRoomService.findByRoomCode(UUID.fromString(roomCode)).orElseThrow(() -> new RuntimeException("Room not found"));
    return pollRepository.findByRoomOrderByTimestampAsc(room);
  }

  public void vote(String roomCode, String email, Map<String, Object> body) {

    Long pollId = Long.valueOf(body.get("pollId").toString());
    int optionIndex = Integer.parseInt(body.get("optionIndex").toString());

    ChatPoll poll = pollRepository.findById(pollId).orElseThrow(() -> new RuntimeException("Poll not found"));

    boolean isMember = chatRoomUserService.getMembers(poll.getRoom()).stream().anyMatch(member -> member.getEmail().equals(email));

    if (!isMember) {
      throw new RuntimeException("User is not a member of the room");
    }

    Optional<ChatVote> existingVote = voteRepository.findByPollAndEmail(poll, email);
    ChatVote vote;

    if (existingVote.isPresent()) {
      vote = existingVote.get();
      vote.setOptionIndex(optionIndex);
    }
    else {
      vote = ChatVote.builder().poll(poll).email(email).optionIndex(optionIndex).build();
    }
    voteRepository.save(vote);
  }

}
