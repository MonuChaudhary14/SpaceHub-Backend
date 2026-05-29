package org.spacehub.service.Interface;

import org.spacehub.DTO.LocalGroup.DeleteLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.JoinLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.LocalGroupMemberDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ILocalGroupService {

  ResponseEntity<ApiResponse<LocalGroupResponse>> createLocalGroup(
    String name,
    String description,
    MultipartFile imageFile
  );

  ResponseEntity<ApiResponse<String>> joinLocalGroup(JoinLocalGroupRequest req);

  ResponseEntity<ApiResponse<String>> deleteLocalGroup(DeleteLocalGroupRequest req);

  ResponseEntity<ApiResponse<List<LocalGroupResponse>>> listAllLocalGroups();

  ResponseEntity<ApiResponse<LocalGroupResponse>> getLocalGroup(UUID id);

  ResponseEntity<ApiResponse<Map<String, Object>>> searchLocalGroups(String q, int page,
                                                                     int size);

  ResponseEntity<?> enterOrJoinLocalGroup(UUID groupId);

  ResponseEntity<ApiResponse<List<LocalGroupMemberDTO>>> getLocalGroupMembers(UUID groupId);

  ResponseEntity<ApiResponse<LocalGroupResponse>> updateLocalGroupSettings(
    UUID groupId,
    MultipartFile imageFile,
    String newName
  );

  ResponseEntity<ApiResponse<Map<String, Boolean>>> checkGroupNameExists(String name);
}
