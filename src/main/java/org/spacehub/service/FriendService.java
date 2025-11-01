package org.spacehub.service;

import org.spacehub.DTO.UserOutput;
import org.spacehub.entities.Friends.Friends;
import org.spacehub.entities.User.User;
import org.spacehub.repository.FriendsRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.Interface.IFriendService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendService implements IFriendService {

  private final FriendsRepository friendsRepository;
  private final UserRepository userRepository;

  public FriendService(FriendsRepository friendsRepository, UserRepository userRepository) {
    this.friendsRepository = friendsRepository;
    this.userRepository = userRepository;
  }

  @CacheEvict(value = {"outgoingRequests"}, allEntries = true)
  public String sendFriendRequest(String userEmail, String friendEmail) {

    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    User friend = userRepository.findByEmail(friendEmail)
            .orElseThrow(() -> new RuntimeException("Friend not found"));

    Optional<Friends> blockCheck = friendsRepository.findByUserAndFriendAndStatus(friend, user, "blocked");
    if (blockCheck.isPresent()) {
      return "Cannot send request. You are blocked by this user.";
    }


    if (friendsRepository.findByUserAndFriend(user, friend).isPresent() ||
            friendsRepository.findByUserAndFriend(friend, user).isPresent()) {
      return "Friend request already exists or you are already friends";
    }

    Friends request = new Friends();
    request.setFriend(friend);
    request.setUser(user);
    request.setStatus("pending");

    friendsRepository.save(request);

    return "Friend request sent successfully";
  }

  @CacheEvict(value = {"incomingRequests"}, allEntries = true)
  public String respondFriendRequest(String userEmail, String requesterEmail, boolean accept) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
    User requester = userRepository.findByEmail(requesterEmail)
            .orElseThrow(() -> new RuntimeException("Requester not found"));

    Friends request = friendsRepository.findByUserAndFriend(requester, user)
            .orElseThrow(() -> new RuntimeException("Friend request not found"));

    if (accept) {
      request.setStatus("accepted");
      friendsRepository.save(request);
      return "Friend request accepted";
    }
    else {
      friendsRepository.delete(request);
      return "Friend request rejected";
    }
  }

  @Cacheable(value = "friends", key = "#userEmail")
  public List<UserOutput> getFriends(String userEmail) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> sent = friendsRepository.findByUserAndStatus(user, "accepted");
    List<Friends> received = friendsRepository.findByFriendAndStatus(user, "accepted");

    List<UserOutput> friendsList = sent.stream()
            .map(friend -> new UserOutput(
                    friend.getFriend().getId(),
                    friend.getFriend().getFirstName(),
                    friend.getFriend().getLastName(),
                    friend.getFriend().getEmail()
            ))
            .collect(Collectors.toList());

    friendsList.addAll(received.stream()
            .map(friend -> new UserOutput(
                    friend.getUser().getId(),
                    friend.getUser().getFirstName(),
                    friend.getUser().getLastName(),
                    friend.getUser().getEmail()
            ))
            .toList());

    return friendsList;
  }

  @CacheEvict(value = {"friends", "incomingRequests"}, allEntries = true)
  public String blockFriend(String userEmail, String friendEmail) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
    User friend = userRepository.findByEmail(friendEmail)
            .orElseThrow(() -> new RuntimeException("Friend not found"));

    Optional<Friends> friendshipOpt = friendsRepository.findByUserAndFriend(user, friend);
    if (friendshipOpt.isEmpty()) {
      friendshipOpt = friendsRepository.findByUserAndFriend(friend, user);
    }

    Friends friendship = friendshipOpt.orElseThrow(() -> new RuntimeException("Friendship not found"));
    friendship.setStatus("blocked");
    friendsRepository.save(friendship);
    return "Friend blocked successfully";
  }

  @Cacheable(value = "incomingRequests", key = "#userEmail")
  public List<UserOutput> getIncomingPendingRequests(String userEmail) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> pendingRequests = friendsRepository.findByFriendAndStatus(user, "pending");

    return pendingRequests.stream()
            .map(f -> new UserOutput(
                    f.getUser().getId(),
                    f.getUser().getFirstName(),
                    f.getUser().getLastName(),
                    f.getUser().getEmail()
            ))
            .collect(Collectors.toList());
  }

  @Cacheable(value = "outgoingRequests", key = "#userEmail")
  public List<UserOutput> getOutgoingPendingRequests(String userEmail) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> sentRequests = friendsRepository.findByUserAndStatus(user, "pending");

    return sentRequests.stream()
            .map(f -> new UserOutput(
                    f.getFriend().getId(),
                    f.getFriend().getFirstName(),
                    f.getFriend().getLastName(),
                    f.getFriend().getEmail()
            ))
            .collect(Collectors.toList());
  }

  @CacheEvict(value = {"friends", "outgoingRequests"}, allEntries = true)
  public String unblockUser(String userEmail, String blockedUserEmail) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
    User blockedUser = userRepository.findByEmail(blockedUserEmail)
            .orElseThrow(() -> new RuntimeException("Blocked user not found"));

    Optional<Friends> blocked = friendsRepository.findByUserAndFriendAndStatus(user, blockedUser, "blocked");
    if (blocked.isPresent()) {
      blocked.get().setStatus("accepted");
      friendsRepository.save(blocked.get());
      return "User unblocked successfully.";
    } else {
      return "No blocked user found.";
    }
  }
}
