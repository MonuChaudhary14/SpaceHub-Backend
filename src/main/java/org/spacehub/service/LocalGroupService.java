package org.spacehub.service;

import org.spacehub.DTO.LocalGroup.CreateLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.DeleteLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.JoinLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.LocalGroupResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.LocalGroupRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Transactional
@Service
public class LocalGroupService {

  private final LocalGroupRepository localGroupRepository;
  private final UserRepository userRepository;

  public static class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
      super(message);
    }
  }

  public LocalGroupService(LocalGroupRepository localGroupRepository, UserRepository userRepository) {
    this.localGroupRepository = localGroupRepository;
    this.userRepository = userRepository;
  }

  public ResponseEntity<ApiResponse<LocalGroupResponse>> createLocalGroup(CreateLocalGroupRequest req) {
    if (req.getName() == null || req.getName().isBlank() || req.getCreatorEmail() == null ||
      req.getCreatorEmail().isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "name and creatorEmail are required", null));
    }

    User creator = userRepository.findByEmail(req.getCreatorEmail())
      .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));

    LocalGroup group = new LocalGroup();
    group.setName(req.getName().trim());
    group.setDescription(req.getDescription());
    group.setCreatedBy(creator);
    group.setCreatedAt(LocalDateTime.now());
    group.setUpdatedAt(LocalDateTime.now());

    group.getMembers().add(creator);

    LocalGroup saved = localGroupRepository.save(group);

    LocalGroupResponse resp = toResponse(saved);
    return ResponseEntity.status(201).body(new ApiResponse<>(201, "Local group created", resp));
  }

  public ResponseEntity<ApiResponse<String>> joinLocalGroup(JoinLocalGroupRequest req) {
    if (req.getGroupId() == null || req.getUserEmail() == null || req.getUserEmail().isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "groupId and userEmail are required", null));
    }

    LocalGroup group = localGroupRepository.findById(req.getGroupId())
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));

    User user = userRepository.findByEmail(req.getUserEmail())
      .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    boolean alreadyMember = group.getMembers().stream().anyMatch(u -> u.getId().equals(user.getId()));
    if (alreadyMember) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, "User already a member",
        null));
    }

    group.getMembers().add(user);
    localGroupRepository.save(group);

    return ResponseEntity.ok(new ApiResponse<>(200, "Joined local group successfully", null));
  }

  public ResponseEntity<ApiResponse<String>> deleteLocalGroup(DeleteLocalGroupRequest req) {
    if (req.getGroupId() == null || req.getRequesterEmail() == null || req.getRequesterEmail().isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "groupId and requesterEmail are required", null));
    }

    LocalGroup group = localGroupRepository.findById(req.getGroupId())
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));

    User requester = userRepository.findByEmail(req.getRequesterEmail())
      .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

    if (!Objects.equals(group.getCreatedBy().getId(), requester.getId())) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Only the group creator can delete this group", null));
    }

    localGroupRepository.delete(group);
    return ResponseEntity.ok(new ApiResponse<>(200, "Local group deleted successfully", null));
  }

  public ResponseEntity<ApiResponse<List<LocalGroupResponse>>> listAllLocalGroups() {
    List<LocalGroup> all = localGroupRepository.findAll();
    List<LocalGroupResponse> out = all.stream().map(this::toResponse).collect(Collectors.toList());
    return ResponseEntity.ok(new ApiResponse<>(200, "Local groups fetched", out));
  }

  public ResponseEntity<ApiResponse<LocalGroupResponse>> getLocalGroup(Long id) {
    LocalGroup group = localGroupRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));
    return ResponseEntity.ok(new ApiResponse<>(200, "Local group fetched", toResponse(group)));
  }

  private LocalGroupResponse toResponse(LocalGroup g) {
    LocalGroupResponse r = new LocalGroupResponse();
    r.setId(g.getId());
    r.setName(g.getName());
    r.setDescription(g.getDescription());
    if (g.getCreatedBy() != null) {
      r.setCreatedByEmail(g.getCreatedBy().getEmail());
    }
    r.setCreatedAt(g.getCreatedAt());
    r.setUpdatedAt(g.getUpdatedAt());
    if (g.getMembers() != null) {
      r.setTotalMembers(g.getMembers().size());
    } else {
      r.setTotalMembers(0);
    }
    List<String> memberEmails = g.getMembers().stream().map(User::getEmail).collect(Collectors.toList());
    r.setMemberEmails(memberEmails);
    return r;
  }
}
