package com.hackathon.distress.controller;

import com.hackathon.distress.entity.AppUser;
import com.hackathon.distress.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    // First visit: create a user. The access key is only ever shown here, the browser stores it.
    @PostMapping
    public ResponseEntity<UserService.NewUser> register() {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.register());
    }

    // Who am I? (also used to check an access key when signing in on another browser)
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        AppUser user = users.require(key);
        return Map.of("callId", user.getCallId(), "createdAt", user.getCreatedAt());
    }
}
