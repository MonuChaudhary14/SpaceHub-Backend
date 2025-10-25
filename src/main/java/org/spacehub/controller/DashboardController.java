package org.spacehub.controller;

import org.spacehub.DTO.DashBoard.UsernameRequest;
import org.spacehub.entities.User.User;
import org.spacehub.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostMapping("/set-username")
    public ResponseEntity<User> setUsername(@RequestBody UsernameRequest request) {
        User updatedUser = dashboardService.saveUsernameByEmail(
                request.getEmail(),
                request.getUsername()
        );
        return ResponseEntity.ok(updatedUser);
    }


}
