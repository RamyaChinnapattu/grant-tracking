package com.grantora.service;

import com.grantora.dto.RegisterRequest;
import com.grantora.dto.LoginRequest;
import com.grantora.entity.Role;
import com.grantora.entity.User;
import com.grantora.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    // Registration
    public User register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();

        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);

        user.setPassword(
                passwordEncoder.encode(request.getPassword()));

        user.setRole(Role.BENEFICIARY);
        user.setActive(true);

        return userRepository.save(user);
    }

    // Login
    public Map<String, Object> login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        }

        String token = jwtService.generateToken(user);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Login successful");
        response.put("token", token);
        response.put("userId", user.getId());
        response.put("fullName", user.getFullName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole().name());
        if (user.getOfficerLevel() != null) {
            response.put("officerLevel", user.getOfficerLevel());
        }
        if (user.getJurisdictionState() != null) {
            response.put("jurisdictionState", user.getJurisdictionState());
        }
        if (user.getJurisdictionDistrict() != null) {
            response.put("jurisdictionDistrict", user.getJurisdictionDistrict());
        }

        return response;
    }
}
