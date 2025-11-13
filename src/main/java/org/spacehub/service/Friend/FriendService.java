package org.spacehub.service.Friend;

import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.User.UserOutput;
import org.spacehub.entities.Friends.Friends;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.entities.User.User;
import org.spacehub.repository.friend.FriendsRepository;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.service.Interface.IFriendService;
import org.spacehub.service.Notification.NotificationService;
import org.spacehub.service.File.S3Service;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.spacehub.DTO.Friend.FriendUpdateDTO;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FriendService implements IFriendService {

  private final FriendsRepository friendsRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;
  private final S3Service s3Service;
  private final SimpMessagingTemplate messagingTemplate;

  public FriendService(FriendsRepository friendsRepository,
                       UserRepository userRepository,
                       NotificationService notificationService,
                       S3Service s3Service,
                       SimpMessagingTemplate messagingTemplate) {
    this.friendsRepository = friendsRepository;
    this.userRepository = userRepository;
    this.notificationService = notificationService;
    this.s3Service = s3Service;
    this.messagingTemplate = messagingTemplate;
  }

  public String sendFriendRequest(String userEmail, String friendEmail) {

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    if (friendEmail == null || friendEmail.isBlank())
      throw new RuntimeException("FriendEmail is required");

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

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    if (requesterEmail == null || requesterEmail.isBlank())
      throw new RuntimeException("RequesterEmail is required");

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

      sendFriendListUpdate(user, requester);
      sendFriendListUpdate(requester, user);

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

  @Transactional
  public List<UserOutput> getFriends(String userEmail) {

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> sent = friendsRepository.findByUserAndStatus(user, "accepted");
    List<Friends> received = friendsRepository.findByFriendAndStatus(user, "accepted");

    List<UserOutput> friendsList = sent.stream()
            .map(friend -> {
              User f = friend.getFriend();
              String preview = buildPresignedUrlSafely(f.getAvatarUrl());
              return new UserOutput(
                      f.getId(),
                      f.getUsername(),
                      f.getEmail(),
                      preview
              );
            }).collect(Collectors.toList());

    friendsList.addAll(received.stream()
            .map(friend -> {
              User f = friend.getUser();
              String preview = buildPresignedUrlSafely(f.getAvatarUrl());
              return new UserOutput(
                      f.getId(),
                      f.getUsername(),
                      f.getEmail(),
                      preview
              );
            }).toList());

    return friendsList;
  }

  public String blockFriend(String userEmail, String friendEmail) {

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    if (friendEmail == null || friendEmail.isBlank())
      throw new RuntimeException("FriendEmail is required");

    if (userEmail.equalsIgnoreCase(friendEmail)) {
      return "You cannot block yourself.";
    }

    User user = getUserByEmail(userEmail, "User not found");
    User friend = getUserByEmail(friendEmail, "Friend not found");

    Friends relationship = findRelationshipBetween(user, friend)
      .orElseGet(() -> {
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

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> pendingRequests = friendsRepository.findByFriendAndStatus(user, "pending");

    return pendingRequests.stream()
            .map(f -> new UserOutput(
                    f.getUser().getId(),
                    f.getUser().getUsername(),
                    f.getUser().getEmail(),
                    f.getUser().getAvatarUrl()
            ))
            .collect(Collectors.toList());
  }

  public List<UserOutput> getOutgoingPendingRequests(String userEmail) {

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Friends> sentRequests = friendsRepository.findByUserAndStatus(user, "pending");

    return sentRequests.stream()
            .map(f -> new UserOutput(
                    f.getFriend().getId(),
                    f.getFriend().getUsername(),
                    f.getFriend().getEmail(),
                    f.getFriend().getAvatarUrl()
            ))
            .collect(Collectors.toList());
  }

  public String unblockUser(String userEmail, String blockedUserEmail) {

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    if (blockedUserEmail == null || blockedUserEmail.isBlank())
      throw new RuntimeException("BlockedUserEmail is required");

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

  public String removeFriend(String userEmail, String friendEmail) {

    if (userEmail == null || userEmail.isBlank())
      throw new RuntimeException("UserEmail is required");

    if (friendEmail == null || friendEmail.isBlank())
      throw new RuntimeException("FriendEmail is required");

    if (userEmail.equalsIgnoreCase(friendEmail)) {
      return "Invalid operation: cannot remove yourself.";
    }

    User user = getUserByEmail(userEmail, "User not found");
    User friend = getUserByEmail(friendEmail, "Friend not found");

    Optional<Friends> rel = findRelationshipBetween(user, friend);

    if (rel.isEmpty()) {
      return "No friend relationship found between the users.";
    }

    friendsRepository.delete(rel.get());
    return "Friend removed successfully.";
  }


  private String buildPresignedUrlSafely(String key) {
    if (key == null || key.isBlank()) return null;
    try {
      return s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
    } catch (Exception ignored) {
      return null;
    }
  }

  private User getUserByEmail(String email, String errorMessage) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException(errorMessage));
  }

  private Optional<Friends> findRelationshipBetween(User user, User friend) {
    Optional<Friends> relationship = friendsRepository.findByUserAndFriend(user, friend);
    if (relationship.isEmpty()) {
      relationship = friendsRepository.findByUserAndFriend(friend, user);
    }
    return relationship;
  }

  private void sendFriendListUpdate(User target, User newFriend) {
    try {
      String avatarUrl = buildPresignedUrlSafely(newFriend.getAvatarUrl());
      FriendUpdateDTO dto = new FriendUpdateDTO(
              newFriend.getId(),
              newFriend.getFirstName(),
              newFriend.getLastName(),
              newFriend.getEmail(),
              avatarUrl
      );
      messagingTemplate.convertAndSendToUser(target.getEmail(), "/queue/friends", dto);
      System.out.println("Friend request send to "+ target.getEmail());
    }
    catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  public boolean areFriends(String email1, String email2) {
    if (email1 == null || email1.isBlank() || email2 == null || email2.isBlank())
      return false;

    User user1 = userRepository.findByEmail(email1).orElse(null);
    User user2 = userRepository.findByEmail(email2).orElse(null);

    if (user1 == null || user2 == null)
      return false;

    return friendsRepository.findByUserAndFriendAndStatus(user1, user2, "accepted").isPresent() || friendsRepository.findByUserAndFriendAndStatus(user2, user1, "accepted").isPresent();
  }

}
