package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatPoll;
import org.spacehub.entities.ChatRoom.ChatVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatVoteRepository extends JpaRepository<ChatVote, Long> {

  Optional<ChatVote> findByPollAndEmail(ChatPoll poll, String email);

}
