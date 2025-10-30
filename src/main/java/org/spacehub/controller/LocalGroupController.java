package org.spacehub.controller;

import org.spacehub.DTO.LocalGroup.CreateLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.DeleteLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.JoinLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.LocalGroupResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.LocalGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/v1/local-group")
public class LocalGroupController {

  private final LocalGroupService localGroupService;

  public LocalGroupController(LocalGroupService localGroupService) {
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
}
