package com.grantora.service;

import com.grantora.entity.User;
import com.grantora.entity.Role;
import com.grantora.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    public User createUser(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()
                || user.getFullName() == null || user.getFullName().isBlank()
                || user.getPassword() == null || user.getPassword().length() < 8
                || user.getRole() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name, email, role, and a password of at least eight characters are required");
        }

        String normalizedEmail = user.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email already registered: " + normalizedEmail);
        }

        user.setEmail(normalizedEmail);
        user.setFullName(user.getFullName().trim());
        if (user.getPhone() != null) {
            user.setPhone(user.getPhone().trim());
        }

        validateOfficerJurisdiction(user);

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        if (user.getActive() == null) {
            user.setActive(true);
        }

        User createdUser = userRepository.save(user);

        auditService.recordAudit(
                "USER_CREATED",
                "ADMIN_MANAGEMENT",
                "User",
                createdUser.getId(),
                "Created user: " + createdUser.getEmail() + " (" + createdUser.getRole()
                        + (createdUser.getOfficerLevel() != null ? "/" + createdUser.getOfficerLevel() : "") + ")");

        return createdUser;
    }

    public User updateUser(Long id, User updatedUser) {
        User existingUser = getUserById(id);

        String newEmail = updatedUser.getEmail() != null ? updatedUser.getEmail().trim().toLowerCase() : null;
        if (newEmail == null || newEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        if (!existingUser.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email already registered: " + newEmail);
        }

        existingUser.setFullName(updatedUser.getFullName() != null ? updatedUser.getFullName().trim() : existingUser.getFullName());
        existingUser.setEmail(newEmail);
        existingUser.setPhone(updatedUser.getPhone() != null ? updatedUser.getPhone().trim() : null);
        existingUser.setRole(updatedUser.getRole());
        existingUser.setActive(updatedUser.getActive());
        existingUser.setOfficerLevel(updatedUser.getOfficerLevel());
        existingUser.setJurisdictionState(updatedUser.getJurisdictionState());
        existingUser.setJurisdictionDistrict(updatedUser.getJurisdictionDistrict());

        validateOfficerJurisdiction(existingUser);

        User savedUser = userRepository.save(existingUser);

        auditService.recordAudit(
                "USER_UPDATED",
                "ADMIN_MANAGEMENT",
                "User",
                savedUser.getId(),
                "Updated user: " + savedUser.getEmail() + " (" + savedUser.getRole()
                        + (savedUser.getOfficerLevel() != null ? "/" + savedUser.getOfficerLevel() : "") + ")");

        return savedUser;
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);

        auditService.recordAudit(
                "USER_DELETED",
                "ADMIN_MANAGEMENT",
                "User",
                id,
                "Deleted user: " + user.getEmail());
    }

    private void validateOfficerJurisdiction(User user) {
        if (user.getRole() != Role.OFFICER) {
            user.setOfficerLevel(null);
            user.setJurisdictionState(null);
            user.setJurisdictionDistrict(null);
            return;
        }

        String level = user.getOfficerLevel();

        if (level == null
                || !List.of("FIELD", "DISTRICT", "FINANCE").contains(level)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Officer level must be FIELD, DISTRICT, or FINANCE");
        }

        if (user.getJurisdictionState() == null
                || user.getJurisdictionState().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Officer jurisdiction state is required");
        }

        if (!"FINANCE".equals(level)
                && (user.getJurisdictionDistrict() == null
                        || user.getJurisdictionDistrict().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Field and district officers require a district");
        }

        if ("FINANCE".equals(level)) {
            user.setJurisdictionDistrict(null);
        }
    }
}
