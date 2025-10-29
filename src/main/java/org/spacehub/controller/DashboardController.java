package org.spacehub.controller;

import jakarta.validation.Valid;
import org.spacehub.DTO.DashBoard.UsernameRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.DashBoardService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashBoardService dashboardService;

    public DashboardController(DashBoardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostMapping("/set-username")
    public ResponseEntity<ApiResponse<String>> setUsername(@Valid @RequestBody UsernameRequest request) {
        ApiResponse<String> resp = dashboardService.saveUsernameByEmail(
                request.getEmail(),
                request.getUsername()
        );
        return ResponseEntity.status(resp.getStatus()).body(resp);
    }

    @PostMapping(value = "/upload-profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadProfileImage(@RequestParam("email") String email, @RequestParam("image") MultipartFile image) {
        ApiResponse<String> response = dashboardService.uploadProfileImage(email, image);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

}