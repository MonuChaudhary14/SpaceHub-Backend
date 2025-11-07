package org.spacehub.service.Interface;

import org.spacehub.DTO.User.UserOutput;
import java.util.List;

public interface IFriendService {

  String sendFriendRequest(String userEmail, String friendEmail);

  String respondFriendRequest(String userEmail, String requesterEmail, boolean accept);

  List<UserOutput> getFriends(String userEmail);

  String blockFriend(String userEmail, String friendEmail);

  List<UserOutput> getIncomingPendingRequests(String userEmail);

  List<UserOutput> getOutgoingPendingRequests(String userEmail);

  String unblockUser(String userEmail, String blockedUserEmail);

  String removeFriend(String userEmail, String friendEmail);

}

