package org.spacehub.service;

import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.UserOutput;
import org.spacehub.entities.Friends.FriendStatus;
import org.spacehub.entities.Friends.Friends;
import org.spacehub.entities.User.User;
import org.spacehub.repository.FriendsRepository;
import org.spacehub.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendService {

    private final FriendsRepository friendsRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FriendService(FriendsRepository friendsRepository, UserRepository userRepository, NotificationService notificationService) {
        this.friendsRepository = friendsRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    private User getUserByEmail(String email) {
        if (email == null || email.isBlank()) throw new RuntimeException("Email cannot be empty");
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    public String sendFriendRequest(String userEmail, String friendEmail) {

        if (userEmail.equals(friendEmail)) throw new RuntimeException("Cannot send friend request to yourself");

        User user = getUserByEmail(userEmail);
        User friend = getUserByEmail(friendEmail);

        Optional<Friends> blocked = friendsRepository.findByUserAndFriendAndStatus(friend, user, FriendStatus.BLOCKED);
        if (blocked.isPresent())
            throw new RuntimeException("Cannot send request. You are blocked by this user.");

        Optional<Friends> existing = friendsRepository.findByUsers(user, friend);
        if (existing.isPresent())
            throw new RuntimeException("Friend request already exists or you are already friends");

        Friends request = new Friends();
        request.setUser(user);
        request.setFriend(friend);
        request.setStatus(FriendStatus.PENDING);

        friendsRepository.save(request);

        notificationService.createNotification(new NotificationRequestDTO(friend.getEmail(),"Friend Request",user.getFirstName() + " " + user.getLastName() + " sent you a friend request","FRIEND_REQUEST",null, request.getId(),null,true
        ));

        return "Friend request sent successfully";
    }

    public String respondFriendRequest(String userEmail, String requesterEmail, boolean accept) {

        User user = getUserByEmail(userEmail);
        User requester = getUserByEmail(requesterEmail);

        Friends request = friendsRepository.findByUserAndFriend(requester, user)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        if (accept) {
            request.setStatus(FriendStatus.ACCEPTED);
            friendsRepository.save(request);

            notificationService.createNotification(new NotificationRequestDTO(
                    requester.getEmail(),
                    "Friend Request Accepted",
                    user.getFirstName() + " " + user.getLastName() + " accepted your friend request",
                    "FRIEND_ACCEPTED",
                    null,
                    request.getId(),
                    null,
                    false
            ));

            return "Friend request accepted";
        }
        else {
            friendsRepository.delete(request);
            return "Friend request rejected";
        }
    }

    public List<UserOutput> getFriends(String userEmail) {

        User user = getUserByEmail(userEmail);

        List<Friends> sent = friendsRepository.findByUserAndStatus(user, FriendStatus.ACCEPTED);
        List<Friends> received = friendsRepository.findByFriendAndStatus(user, FriendStatus.ACCEPTED);

        List<UserOutput> friendsList = sent.stream()
                .map(f -> new UserOutput(
                        f.getFriend().getId(),
                        f.getFriend().getFirstName(),
                        f.getFriend().getLastName(),
                        f.getFriend().getEmail()
                )).collect(Collectors.toList());

        friendsList.addAll(received.stream()
                .map(f -> new UserOutput(
                        f.getUser().getId(),
                        f.getUser().getFirstName(),
                        f.getUser().getLastName(),
                        f.getUser().getEmail()
                )).toList());

        return friendsList;
    }

    public String blockFriend(String userEmail, String friendEmail) {
        User user = getUserByEmail(userEmail);
        User friend = getUserByEmail(friendEmail);

        Optional<Friends> friendshipOpt = friendsRepository.findByUserAndFriend(user, friend);
        if (friendshipOpt.isEmpty()) friendshipOpt = friendsRepository.findByUserAndFriend(friend, user);

        Friends friendship = friendshipOpt.orElseThrow(() -> new RuntimeException("Friendship not found"));
        friendship.setStatus(FriendStatus.BLOCKED);
        friendsRepository.save(friendship);
        return "Friend blocked successfully";
    }

    public List<UserOutput> getIncomingPendingRequests(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Friends> pending = friendsRepository.findByFriendAndStatus(user, FriendStatus.PENDING);

        return pending.stream()
                .map(f -> new UserOutput(
                        f.getUser().getId(),
                        f.getUser().getFirstName(),
                        f.getUser().getLastName(),
                        f.getUser().getEmail()
                )).collect(Collectors.toList());
    }

    public List<UserOutput> getOutgoingPendingRequests(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Friends> sentRequests = friendsRepository.findByUserAndStatus(user, FriendStatus.PENDING);

        return sentRequests.stream()
                .map(f -> new UserOutput(
                        f.getFriend().getId(),
                        f.getFriend().getFirstName(),
                        f.getFriend().getLastName(),
                        f.getFriend().getEmail()
                )).collect(Collectors.toList());
    }

    public String unblockUser(String userEmail, String blockedUserEmail) {
        User user = getUserByEmail(userEmail);
        User blockedUser = getUserByEmail(blockedUserEmail);

        Optional<Friends> blocked = friendsRepository.findByUserAndFriendAndStatus(user, blockedUser, FriendStatus.BLOCKED);
        if (blocked.isPresent()) {
            blocked.get().setStatus(FriendStatus.ACCEPTED);
            friendsRepository.save(blocked.get());
            return "User unblocked successfully.";
        }
        else {
            return "No blocked user found.";
        }
    }

}