package com.grantora.service;

import com.grantora.dto.LoginRequest;
import com.grantora.dto.RegisterRequest;
import com.grantora.entity.Role;
import com.grantora.entity.User;
import com.grantora.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthValidationAndSecurityTest {

    private UserRepository userRepository;
    private JwtService jwtService;
    private AuthService authService;
    private Validator validator;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(userRepository, jwtService);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void register_shouldSucceedWithValidData() {
        RegisterRequest request = new RegisterRequest(
                "Jane Citizen", "jane@example.com", "SecurePass123!", "+919876543210");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setId(user, 10L);
            return user;
        });

        User registered = authService.register(request);

        assertNotNull(registered);
        assertEquals("Jane Citizen", registered.getFullName());
        assertEquals("jane@example.com", registered.getEmail());
        assertEquals(Role.BENEFICIARY, registered.getRole());
        assertTrue(registered.getActive());
        // Verify BCrypt password hashing
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("SecurePass123!", registered.getPassword()));
        assertNotEquals("SecurePass123!", registered.getPassword());
    }

    @Test
    void register_shouldThrowConflictWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "Duplicate User", "existing@example.com", "SecurePass123!", "+919876543210");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Email already registered"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldRejectWhenAdminRoleRequested() {
        RegisterRequest request = new RegisterRequest(
                "Escalation Attempt", "attacker@example.com", "SecurePass123!", "+919876543210");
        request.setRole("ADMIN");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Self-registration is only permitted for the BENEFICIARY role"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldRejectWhenOfficerRoleRequested() {
        RegisterRequest request = new RegisterRequest(
                "Escalation Attempt", "officer.fraud@example.com", "SecurePass123!", "+919876543210");
        request.setRole("OFFICER");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Self-registration is only permitted for the BENEFICIARY role"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldAllowExplicitBeneficiaryRole() {
        RegisterRequest request = new RegisterRequest(
                "Explicit Beneficiary", "explicit@example.com", "SecurePass123!", "+919876543210");
        request.setRole("BENEFICIARY");

        when(userRepository.existsByEmail("explicit@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = authService.register(request);
        assertNotNull(registered);
        assertEquals(Role.BENEFICIARY, registered.getRole());
    }

    @Test
    void login_shouldSucceedWithValidCredentials() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        setId(user, 5L);
        user.setEmail("user@example.com");
        user.setFullName("Valid User");
        user.setPassword(encoder.encode("Password123!"));
        user.setRole(Role.BENEFICIARY);
        user.setActive(true);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("mock-jwt-token");

        LoginRequest request = new LoginRequest("user@example.com", "Password123!");
        Map<String, Object> response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.get("token"));
        assertEquals("user@example.com", response.get("email"));
        assertEquals("BENEFICIARY", response.get("role"));
        assertFalse(response.containsKey("password"));
    }

    @Test
    void login_shouldThrowUnauthorizedWhenPasswordIsIncorrect() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword(encoder.encode("CorrectPassword123"));
        user.setActive(true);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request));

        assertEquals(401, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid email or password"));
    }

    @Test
    void login_shouldThrowUnauthorizedWhenEmailNotFound() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nonexistent@example.com", "Password123!");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request));

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void login_shouldThrowForbiddenWhenAccountIsInactive() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setEmail("inactive@example.com");
        user.setPassword(encoder.encode("Password123!"));
        user.setActive(false);

        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("inactive@example.com", "Password123!");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request));

        assertEquals(403, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("inactive"));
    }

    @Test
    void registerRequestValidation_shouldCatchInvalidData() {
        // Missing name
        RegisterRequest req1 = new RegisterRequest("", "valid@example.com", "Password123", "9876543210");
        assertFalse(validator.validate(req1).isEmpty());

        // Invalid email
        RegisterRequest req2 = new RegisterRequest("Valid Name", "invalid-email", "Password123", "9876543210");
        assertFalse(validator.validate(req2).isEmpty());

        // Password too short (< 8 chars)
        RegisterRequest req3 = new RegisterRequest("Valid Name", "valid@example.com", "short", "9876543210");
        assertFalse(validator.validate(req3).isEmpty());

        // Valid data
        RegisterRequest validReq = new RegisterRequest("Valid Name", "valid@example.com", "Password123", "9876543210");
        assertTrue(validator.validate(validReq).isEmpty());
    }

    @Test
    void loginRequestValidation_shouldCatchInvalidData() {
        // Missing email
        LoginRequest req1 = new LoginRequest("", "Password123");
        assertFalse(validator.validate(req1).isEmpty());

        // Invalid email format
        LoginRequest req2 = new LoginRequest("notanemail", "Password123");
        assertFalse(validator.validate(req2).isEmpty());

        // Missing password
        LoginRequest req3 = new LoginRequest("valid@example.com", "");
        assertFalse(validator.validate(req3).isEmpty());

        // Valid
        LoginRequest validReq = new LoginRequest("valid@example.com", "Password123");
        assertTrue(validator.validate(validReq).isEmpty());
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
