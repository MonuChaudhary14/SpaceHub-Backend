package org.spacehub.service;

import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.User.UserOutput;
import org.spacehub.entities.Friends.Friends;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.entities.User.User;
import org.spacehub.repository.FriendsRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.Interface.IFriendService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FriendService implements IFriendService {

  private final FriendsRepository friendsRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  public FriendService(FriendsRepository friendsRepository, UserRepository userRepository,
                       NotificationService notificationService) {
    this.friendsRepository = friendsRepository;
    this.userRepository = userRepository;
    this.notificationService = notificationService;
  }

  public String sendFriendRequest(String userEmail, String friendEmail) {

    if (userEmail.equalsIgnoreCase(friendEmail)) {
      return "You cannot send a friend request to yourself.";
    }

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
      return "Friend request already exists or you are already friends.";
    }

    Friends request = new Friends();
    request.setUser(user);
    request.setFriend(friend);
    request.setStatus("pending");

    friendsRepository.save(request);

    notificationService.sendFriendRequestNotification(user, friend);

    return "Friend request sent successfully.";
  }

  public String respondFriendRequest(String userEmail, String requesterEmail, boolean accept) {
    if (userEmail.equalsIgnoreCase(requesterEmail)) {
      return "Invalid operation.";
    }

    User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

    User requester = userRepository.findByEmail(requesterEmail).orElseThrow(() ->
      new RuntimeException("Requester not found"));

    Friends request = friendsRepository.findByUserAndFriend(requester, user).orElseThrow(() ->
      new RuntimeException("Friend request not found"));

    if (!"pending".equals(request.getStatus())) {
      return "This request is no longer pending.";
    }

    if (accept) {
      request.setStatus("accepted");
      friendsRepository.save(request);

      NotificationRequestDTO notification = NotificationRequestDTO.builder()
              .senderEmail(user.getEmail())
              .email(requester.getEmail())
              .type(NotificationType.FRIEND_ACCEPTED)
              .title("Friend Request Accepted")
              .message(user.getFirstName() + " accepted your friend request.")
              .scope("friend")
              .actionable(false)
              .referenceId(UUID.randomUUID())
              .build();
      notificationService.createNotification(notification);

      return "Friend request accepted";
    }
    else {
      friendsRepository.delete(request);

      NotificationRequestDTO notification = NotificationRequestDTO.builder()
              .senderEmail(user.getEmail())
              .email(requester.getEmail())
              .type(NotificationType.FRIEND_REJECTED)
              .title("Friend Request Rejected")
              .message(user.getFirstName() + " rejected your friend request.")
              .scope("friend")
              .actionable(false)
              .referenceId(UUID.randomUUID())
              .build();
      notificationService.createNotification(notification);

      return "Friend request rejected.";
    }
  }

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
                    friend.getFriend().getEmail(),
                    friend.getFriend().getAvatarUrl()
            ))
            .collect(Collectors.toList());

    friendsList.addAll(received.stream()
            .map(friend -> new UserOutput(
                    friend.getUser().getId(),
                    friend.getUser().getFirstName(),
                    friend.getUser().getLastName(),
                    friend.getUser().getEmail(),
                    friend.getUser().getAvatarUrl()
            ))
            .toList());

    return friendsList;
  }

  public String blockFriend(String userEmail, String friendEmail) {

    if (userEmail.equalsIgnoreCase(friendEmail)) {
      return "You cannot block yourself.";
    }

    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
    User friend = userRepository.findByEmail(friendEmail)
            .orElseThrow(() -> new RuntimeException("Friend not found"));

    Optional<Friends> existing = friendsRepository.findByUserAndFriend(user, friend);
    if (existing.isEmpty()) {
      existing = friendsRepository.findByUserAndFriend(friend, user);
    }

    Friends relationship = existing.orElseGet(() -> {
      Friends newFriend = new Friends();
      newFriend.setUser(user);
      newFriend.setFriend(friend);
      return newFriend;
    });

    relationship.setStatus("blocked");
    friendsRepository.save(relationship);
    return "User blocked successfully.";
  }

  public List<UserOutput> getIncomingPendingRequests(String userEmail) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> pendingRequests = friendsRepository.findByFriendAndStatus(user, "pending");

    return pendingRequests.stream()
            .map(f -> new UserOutput(
                    f.getUser().getId(),
                    f.getUser().getFirstName(),
                    f.getUser().getLastName(),
                    f.getUser().getEmail(),
                    f.getUser().getAvatarUrl()
            ))
            .collect(Collectors.toList());
  }

  public List<UserOutput> getOutgoingPendingRequests(String userEmail) {
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> sentRequests = friendsRepository.findByUserAndStatus(user, "pending");

    return sentRequests.stream()
            .map(f -> new UserOutput(
                    f.getFriend().getId(),
                    f.getFriend().getFirstName(),
                    f.getFriend().getLastName(),
                    f.getFriend().getEmail(),
                    f.getUser().getAvatarUrl()
            ))
            .collect(Collectors.toList());
  }

  public String unblockUser(String userEmail, String blockedUserEmail) {
    if (userEmail.equalsIgnoreCase(blockedUserEmail)) {
      return "You cannot unblock yourself.";
    }

    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
    User blockedUser = userRepository.findByEmail(blockedUserEmail)
            .orElseThrow(() -> new RuntimeException("Blocked user not found"));

    Optional<Friends> blocked = friendsRepository.findByUserAndFriendAndStatus(user, blockedUser, "blocked");
    if (blocked.isPresent()) {
      friendsRepository.delete(blocked.get());
      return "User unblocked successfully.";
    } else {
      return "No blocked user found.";
    }
  }
}
