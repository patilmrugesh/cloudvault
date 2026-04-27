package com.cloudvault.controller;

import com.cloudvault.model.User;
import com.cloudvault.repository.UserRepository;
import com.cloudvault.security.JwtUtils;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(encoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .build();

        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Error: User not found!"));

        if (encoder.matches(request.getPassword(), user.getPassword())) {
            String jwt = jwtUtils.generateToken(user.getUsername());
            return ResponseEntity.ok(Map.of("token", jwt, "username", user.getUsername()));
        } else {
            return ResponseEntity.status(401).body("Error: Invalid credentials");
        }
    }
}

// Simple DTO for request body
@Data
class AuthRequest {
    private String username;
    private String password;
}