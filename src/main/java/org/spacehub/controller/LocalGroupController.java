package org.spacehub.controller;

import org.spacehub.DTO.LocalGroup.DeleteLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.JoinLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.LocalGroupResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.ILocalGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/v1/local-group")
public class LocalGroupController {

  private final ILocalGroupService localGroupService;

  public LocalGroupController(ILocalGroupService localGroupService) {
    this.localGroupService = localGroupService;
  }

  @PostMapping("/create")
  public ResponseEntity<ApiResponse<LocalGroupResponse>> createLocalGroup(
    @RequestParam("name") String name,
    @RequestParam("description") String description,
    @RequestParam("creatorEmail") String creatorEmail,
    @RequestParam("imageFile") MultipartFile imageFile) {
    return localGroupService.createLocalGroup(name, description, creatorEmail, imageFile);
  }


  @PostMapping("/join")
  public ResponseEntity<ApiResponse<String>> joinLocalGroup(@RequestBody JoinLocalGroupRequest request) {
    return localGroupService.joinLocalGroup(request);
  }

  @DeleteMapping("/delete")
  public ResponseEntity<ApiResponse<String>> deleteLocalGroup(@RequestBody DeleteLocalGroupRequest request) {
    return localGroupService.deleteLocalGroup(request);
  }

  @GetMapping("/all")
  public ResponseEntity<ApiResponse<List<LocalGroupResponse>>> listAllLocalGroups() {
    return localGroupService.listAllLocalGroups();
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<LocalGroupResponse>> getLocalGroup(@PathVariable("id") Long id) {
    return localGroupService.getLocalGroup(id);
  }

  @GetMapping("/search")
  public ResponseEntity<?> searchLocalGroups(
    @RequestParam("q") String q,
    @RequestParam(value = "requesterEmail", required = false) String requesterEmail,
    @RequestParam(value = "page", defaultValue = "0") int page,
    @RequestParam(value = "size", defaultValue = "20") int size) {
    return localGroupService.searchLocalGroups(q, requesterEmail, page, size);
  }

  @PostMapping("/{id}/enter")
  public ResponseEntity<?> enterLocalGroup(
    @PathVariable("id") Long groupId,
    @RequestParam("requesterEmail") String requesterEmail) {
    return localGroupService.enterOrJoinLocalGroup(groupId, requesterEmail);
  }
}
