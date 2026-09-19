package org.spacehub.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.entities.Friends.Friends;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.entities.User.User;
import org.spacehub.entities.User.UserRole;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.ChatRoom.MessageRepository;
import org.spacehub.repository.Notification.NotificationRepository;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.repository.friend.FriendsRepository;
import org.spacehub.repository.localgroup.LocalGroupRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

  private final UserRepository userRepository;
  private final CommunityRepository communityRepository;
  private final CommunityUserRepository communityUserRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final LocalGroupRepository localGroupRepository;
  private final FriendsRepository friendsRepository;
  private final MessageRepository messageRepository;
  private final NotificationRepository notificationRepository;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbcTemplate;

  @Override
  public void run(String... args) {
    try {
      log.info("Checking database for mock data seeding with rich media...");
      dropLegacyCheckConstraints();
      initMockData();
      log.info("Mock data initialization completed successfully.");
    }
    catch (Exception e) {
      log.warn("Mock data seeding encountered an issue (continuing startup): {}", e.getMessage());
    }
  }

  private void dropLegacyCheckConstraints() {
    try {
      jdbcTemplate.execute("ALTER TABLE community_user DROP CONSTRAINT IF EXISTS community_user_role_check");
      jdbcTemplate.execute("ALTER TABLE chat_room_user DROP CONSTRAINT IF EXISTS chat_room_user_role_check");
      jdbcTemplate.execute("UPDATE community_user SET role = 'OWNER' WHERE role IN ('WORKSPACE_OWNER', 'COMMUNITY_OWNER')");
      jdbcTemplate.execute("UPDATE chat_room_user SET role = 'OWNER' WHERE role IN ('WORKSPACE_OWNER', 'COMMUNITY_OWNER')");
      jdbcTemplate.execute("ALTER TABLE voice_room DROP CONSTRAINT IF EXISTS uk88mh4q2ke232n32n2dxs91tqm");
      jdbcTemplate.execute("ALTER TABLE voice_room DROP CONSTRAINT IF EXISTS voice_room_janus_room_id_key");
      jdbcTemplate.execute("ALTER TABLE voice_room ALTER COLUMN janus_room_id DROP NOT NULL");
    }
    catch (Exception e) {
      log.warn("Could not migrate legacy roles or drop constraints: {}", e.getMessage());
    }
  }

  private void initMockData() {
    User monu = createOrGetUser(
      "monuchaudharypoonia@gmail.com",
      "monuchaudhary",
      "Monu",
      "Chaudhary",
      "@Monu1402",
      UserRole.ADMIN,
      "SpaceHub Architect & Developer | Distributed Systems",
      "Bangalore, India",
      "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80",
      "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?auto=format&fit=crop&w=1200&q=80"
    );

    User alex = createOrGetUser(
      "alex.chen@spacehub.dev",
      "alexchen",
      "Alex",
      "Chen",
      "Password@123",
      UserRole.USER,
      "Distributed Systems Engineer & Open Source Enthusiast",
      "San Francisco, USA",
      "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=300&q=80",
      "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1200&q=80"
    );

    User sarah = createOrGetUser(
      "sarah.jenkins@spacehub.dev",
      "sarahj",
      "Sarah",
      "Jenkins",
      "Password@123",
      UserRole.USER,
      "Lead Product Designer @ SpaceHub | UI/UX & WebRTC",
      "London, UK",
      "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=300&q=80",
      "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop&w=1200&q=80"
    );

    User marcus = createOrGetUser(
      "dev.marcus@spacehub.dev",
      "marcusdev",
      "Marcus",
      "Vance",
      "Password@123",
      UserRole.USER,
      "AI & Real-Time Media Researcher | WebRTC/LiveKit Hacker",
      "Berlin, Germany",
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80",
      "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=1200&q=80"
    );

    User demo = createOrGetUser(
      "demo.user@spacehub.dev",
      "demouser",
      "Demo",
      "User",
      "Password@123",
      UserRole.USER,
      "SpaceHub Platform Explorer & Tester",
      "New York, USA",
      "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80",
      "https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1200&q=80"
    );

    User admin = createOrGetUser(
      "admin@spacehub.dev",
      "admin",
      "Admin",
      "SpaceHub",
      "Password@123",
      UserRole.ADMIN,
      "SpaceHub System Administrator",
      "San Francisco, USA",
      "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80",
      "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=1200&q=80"
    );

    initFriends(monu, alex, sarah, marcus);

    initCommunities(monu, alex, sarah, marcus);

    initLocalGroups(monu, alex, sarah);

    initDirectMessages(monu, alex, sarah, marcus);

    initNotifications(monu, alex, sarah);
  }

  private User createOrGetUser(
    String email,
    String username,
    String firstName,
    String lastName,
    String rawPassword,
    UserRole role,
    String bio,
    String location,
    String avatarUrl,
    String coverPhotoUrl
  ) {
    Optional<User> existing = userRepository.findByEmail(email.trim().toLowerCase());
    if (existing.isPresent()) {
      User u = existing.get();
      boolean changed = false;
      if (!Boolean.TRUE.equals(u.getEnabled())) {
        u.setEnabled(true);
        changed = true;
      }
      if (Boolean.TRUE.equals(u.getLocked())) {
        u.setLocked(false);
        changed = true;
      }
      if (!Boolean.TRUE.equals(u.getIsVerifiedRegistration())) {
        u.setIsVerifiedRegistration(true);
        changed = true;
      }
      if (!Boolean.TRUE.equals(u.getIsVerifiedLogin())) {
        u.setIsVerifiedLogin(true);
        changed = true;
      }
      if (!Boolean.TRUE.equals(u.getIsVerifiedForgot())) {
        u.setIsVerifiedForgot(true);
        changed = true;
      }
      if (u.getPasswordVersion() == null) {
        u.setPasswordVersion(1);
        changed = true;
      }
      if (u.getPassword() == null || !passwordEncoder.matches(rawPassword, u.getPassword())) {
        u.setPassword(passwordEncoder.encode(rawPassword));
        changed = true;
      }
      if (u.getAvatarUrl() == null || u.getAvatarUrl().isBlank()) {
        u.setAvatarUrl(avatarUrl);
        changed = true;
      }
      if (u.getCoverPhotoUrl() == null || u.getCoverPhotoUrl().isBlank()) {
        u.setCoverPhotoUrl(coverPhotoUrl);
        changed = true;
      }
      if (changed) {
        userRepository.save(u);
      }
      return u;
    }

    User user = new User();
    user.setEmail(email.trim().toLowerCase());
    user.setUsername(username);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setPassword(passwordEncoder.encode(rawPassword));
    user.setUserRole(role);
    user.setEnabled(true);
    user.setLocked(false);
    user.setIsVerifiedRegistration(true);
    user.setIsVerifiedLogin(true);
    user.setIsVerifiedForgot(true);
    user.setPasswordVersion(1);
    user.setBio(bio);
    user.setLocation(location);
    user.setAvatarUrl(avatarUrl);
    user.setCoverPhotoUrl(coverPhotoUrl);
    user.setCreatedAt(LocalDateTime.now().minusDays(30));
    user.setUpdatedAt(LocalDateTime.now());
    return userRepository.save(user);
  }

  private void initFriends(User monu, User alex, User sarah, User marcus) {
    createFriendship(monu, alex);
    createFriendship(monu, sarah);
    createFriendship(monu, marcus);
    createFriendship(alex, sarah);
  }

  private void createFriendship(User u1, User u2) {
    Optional<Friends> f1 = friendsRepository.findByUserAndFriend(u1, u2);
    Optional<Friends> f2 = friendsRepository.findByUserAndFriend(u2, u1);

    if (f1.isEmpty() && f2.isEmpty()) {
      Friends rel = new Friends();
      rel.setUser(u1);
      rel.setFriend(u2);
      rel.setStatus("ACCEPTED");
      rel.setCreatedAt(LocalDateTime.now().minusDays(10));
      friendsRepository.save(rel);
    }
    else if (f1.isPresent() && f2.isPresent()) {
      friendsRepository.delete(f2.get());
    }
  }

  private void initCommunities(User monu, User alex, User sarah, User marcus) {
    createOrGetCommunity(
      "SpaceHub Central",
      "The official SpaceHub community for distributed architecture, engineering discussions, and live collaboration.",
      monu,
      "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80",
      List.of(
        new MemberRole(monu, Role.OWNER),
        new MemberRole(alex, Role.ADMIN),
        new MemberRole(sarah, Role.MODERATOR),
        new MemberRole(marcus, Role.MEMBER)
      ),
      List.of("announcements", "general", "dev-chat", "architecture-lounge")
    );

    createOrGetCommunity(
      "AI & Distributed Systems",
      "Deep-dives into Redis Pub/Sub backplanes, WebRTC SFU streaming, Token Bucket rate limiting, and PostGIS queries.",
      alex,
      "https://images.unsplash.com/photo-1620712943543-bcc4688e7485?auto=format&fit=crop&w=800&q=80",
      List.of(
        new MemberRole(alex, Role.OWNER),
        new MemberRole(monu, Role.ADMIN),
        new MemberRole(marcus, Role.MODERATOR)
      ),
      List.of("kafka-streams", "webrtc-livekit", "system-design", "whitepapers")
    );

    createOrGetCommunity(
      "UI/UX & Design Lab",
      "Crafting hyper-polished interfaces, fluid glassmorphism animations, and dynamic community workflows.",
      sarah,
      "https://images.unsplash.com/photo-1558655146-d09347e92766?auto=format&fit=crop&w=800&q=80",
      List.of(
        new MemberRole(sarah, Role.OWNER),
        new MemberRole(monu, Role.ADMIN),
        new MemberRole(alex, Role.MODERATOR)
      ),
      List.of("design-critique", "prototypes", "theme-engine")
    );
  }

  private record MemberRole(User user, Role role) {
  }

  private Community createOrGetCommunity(
    String name,
    String description,
    User creator,
    String imageUrl,
    List<MemberRole> members,
    List<String> roomNames
  ) {
    Community community = communityRepository.findByName(name);
    if (community == null) {
      community = new Community();
      community.setName(name);
      community.setDescription(description);
      community.setCreatedBy(creator);
      community.setImageUrl(imageUrl);
      community.setCommunityId(UUID.randomUUID());
      community.setCreatedAt(LocalDateTime.now().minusDays(15));
      community.setUpdatedAt(LocalDateTime.now());
      community = communityRepository.save(community);
    }
    else {
      boolean updated = false;
      if (community.getImageUrl() == null || community.getImageUrl().isBlank()) {
        community.setImageUrl(imageUrl);
        updated = true;
      }
      if (updated) {
        communityRepository.save(community);
      }
    }

    for (MemberRole mr : members) {
      Optional<CommunityUser> cuOpt = communityUserRepository.findByCommunityAndUser(community, mr.user());
      if (cuOpt.isEmpty()) {
        CommunityUser cu = new CommunityUser();
        cu.setCommunity(community);
        cu.setUser(mr.user());
        cu.setRole(mr.role());
        cu.setJoinDate(LocalDateTime.now().minusDays(10));
        cu.setBlocked(false);
        cu.setBanned(false);
        communityUserRepository.save(cu);
      }
      else {
        CommunityUser cu = cuOpt.get();
        if (cu.getRole() != mr.role()) {
          cu.setRole(mr.role());
          communityUserRepository.save(cu);
        }
      }
    }

    for (String roomName : roomNames) {
      final String trimmedName = roomName.trim();
      final UUID commId = community.getId();
      boolean exists = chatRoomRepository.findByCommunityId(commId).stream()
        .anyMatch(r -> r.getName().equalsIgnoreCase(trimmedName));

      if (!exists) {
        ChatRoom room = new ChatRoom();
        room.setName(trimmedName);
        room.setCommunity(community);
        room.setRoomCode(UUID.randomUUID());
        chatRoomRepository.save(room);
      }
    }

    return community;
  }

  private void initLocalGroups(User monu, User alex, User sarah) {
    createOrGetLocalGroup(
      "Bangalore Tech Connect",
      "Local tech meetups, hackathons, and coffee discussions for builders in Bangalore.",
      monu,
      "https://images.unsplash.com/photo-1596176530529-78163a4f7af2?auto=format&fit=crop&w=800&q=80",
      Set.of(monu, alex)
    );

    createOrGetLocalGroup(
      "SF Builders Club",
      "Founders, builders, and engineers hacking on real-time web applications in the Bay Area.",
      alex,
      "https://images.unsplash.com/photo-1501594907352-04cda38ebc29?auto=format&fit=crop&w=800&q=80",
      Set.of(alex, sarah, monu)
    );
  }

  private void createOrGetLocalGroup(
    String name,
    String description,
    User creator,
    String imageUrl,
    Set<User> members
  ) {
    Optional<LocalGroup> existing = localGroupRepository.findAll().stream()
      .filter(g -> g.getName().equalsIgnoreCase(name))
      .findFirst();

    if (existing.isEmpty()) {
      LocalGroup group = new LocalGroup();
      group.setName(name);
      group.setDescription(description);
      group.setCreatedBy(creator);
      group.setImageUrl(imageUrl);
      group.setInviteCode("LG-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
      group.setCreatedAt(LocalDateTime.now().minusDays(5));
      group.setUpdatedAt(LocalDateTime.now());
      group.setMembers(new HashSet<>(members));
      localGroupRepository.save(group);
    }
    else {
      LocalGroup g = existing.get();
      if (g.getImageUrl() == null || g.getImageUrl().isBlank()) {
        g.setImageUrl(imageUrl);
        localGroupRepository.save(g);
      }
    }
  }

  private void initDirectMessages(User monu, User alex, User sarah, User marcus) {
    if (messageRepository.count() > 0) {
      return;
    }

    long now = System.currentTimeMillis();
    long minute = 60 * 1000L;

    createDirectMessage(
      alex.getEmail(),
      monu.getEmail(),
      "Hey Monu! How is the distributed WebSocket fanout architecture coming along?",
      now - 60 * minute
    );
    createDirectMessage(
      monu.getEmail(),
      alex.getEmail(),
      "Hey Alex! Just configured Redis Pub/Sub message backplane and verified sub-50ms delivery latency.",
      now - 50 * minute
    );
    createDirectMessage(
      alex.getEmail(),
      monu.getEmail(),
      "Awesome! That will easily scale across multiple Spring Boot container instances.",
      now - 45 * minute
    );

    createDirectMessage(
      sarah.getEmail(),
      monu.getEmail(),
      "Hi Monu, I updated the community dashboard UI and voice room layout design tokens.",
      now - 30 * minute
    );
    createDirectMessage(
      monu.getEmail(),
      sarah.getEmail(),
      "Looks fantastic Sarah! The theme transitions and glassmorphism styling feel super premium.",
      now - 20 * minute
    );

    createDirectMessage(
      marcus.getEmail(),
      monu.getEmail(),
      "Hey! I'm testing the LiveKit WebRTC SFU audio/video room integration.",
      now - 10 * minute
    );
  }

  private void createDirectMessage(String sender, String receiver, String content, long timestamp) {
    Message message = Message.builder()
      .messageUuid(UUID.randomUUID().toString())
      .senderEmail(sender)
      .receiverEmail(receiver)
      .content(content)
      .timestamp(timestamp)
      .type("MESSAGE")
      .readStatus(true)
      .senderDeleted(false)
      .receiverDeleted(false)
      .build();
    messageRepository.save(message);
  }

  private void initNotifications(User monu, User alex, User sarah) {
    if (notificationRepository.count() > 0) {
      return;
    }

    Notification n1 = Notification.builder()
      .publicId(UUID.randomUUID())
      .recipient(monu)
      .sender(alex)
      .title("Welcome to SpaceHub")
      .message("Alex Chen joined your network on SpaceHub.")
      .type(NotificationType.FRIEND_ACCEPTED)
      .scope("social")
      .read(false)
      .actionable(false)
      .createdAt(LocalDateTime.now().minusHours(2))
      .build();

    Notification n2 = Notification.builder()
      .publicId(UUID.randomUUID())
      .recipient(monu)
      .sender(sarah)
      .title("New Community Room")
      .message("Sarah Jenkins created room #design-critique in UI/UX & Design Lab.")
      .type(NotificationType.COMMUNITY)
      .scope("community")
      .read(false)
      .actionable(false)
      .createdAt(LocalDateTime.now().minusMinutes(30))
      .build();

    notificationRepository.save(n1);
    notificationRepository.save(n2);
  }
}
