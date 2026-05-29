package org.spacehub.service.Interface;

import org.spacehub.DTO.User.UserOutput;
import java.util.List;

public interface IFriendService {

  String sendFriendRequest(String friendEmail);

  String respondFriendRequest(String requesterEmail, boolean accept);

  List<UserOutput> getFriends();

  String blockFriend(String friendEmail);

  List<UserOutput> getIncomingPendingRequests();

  List<UserOutput> getOutgoingPendingRequests();

  String unblockUser(String blockedUserEmail);

  String removeFriend(String friendEmail);

}

