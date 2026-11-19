package com.grantora.service;

import com.grantora.entity.Role;
import com.grantora.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceProductionProtectionTest {

    private static final String VALID_CUSTOM_SECRET =
            Base64.getEncoder().encodeToString(
                    "a-very-strong-production-secret-key-32bytes!".getBytes());

    @Test
    @DisplayName("dev profile with fallback secret: allowed")
    void devProfile_withFallbackSecret_shouldBeAllowed() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        JwtService jwtService = new JwtService(JwtService.DEV_FALLBACK_SECRET, 86400000L, devEnv);

        User user = new User();
        setId(user, 1L);
        user.setEmail("dev@test.local");
        user.setRole(Role.BENEFICIARY);

        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertEquals("dev@test.local", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token, "dev@test.local"));
    }

    @Test
    @DisplayName("local profile with fallback secret: allowed")
    void localProfile_withFallbackSecret_shouldBeAllowed() {
        MockEnvironment localEnv = new MockEnvironment();
        localEnv.setActiveProfiles("local");

        JwtService jwtService = new JwtService(JwtService.DEV_FALLBACK_SECRET, 86400000L, localEnv);
        assertNotNull(jwtService);
    }

    @Test
    @DisplayName("test profile with fallback secret: allowed")
    void testProfile_withFallbackSecret_shouldBeAllowed() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        JwtService jwtService = new JwtService(JwtService.DEV_FALLBACK_SECRET, 86400000L, testEnv);
        assertNotNull(jwtService);
    }

    @Test
    @DisplayName("prod profile with fallback secret: rejected")
    void prodProfile_withFallbackSecret_shouldBeRejected() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(JwtService.DEV_FALLBACK_SECRET, 86400000L, prodEnv));

        assertTrue(ex.getMessage().contains("development fallback JWT secret is only permitted"));
        assertFalse(ex.getMessage().contains(JwtService.DEV_FALLBACK_SECRET));
    }

    @Test
    @DisplayName("no active profile with fallback secret: rejected")
    void noActiveProfile_withFallbackSecret_shouldBeRejected() {
        MockEnvironment env = new MockEnvironment();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(JwtService.DEV_FALLBACK_SECRET, 86400000L, env));

        assertTrue(ex.getMessage().contains("development fallback JWT secret is only permitted"));
        assertFalse(ex.getMessage().contains(JwtService.DEV_FALLBACK_SECRET));
    }

    @Test
    @DisplayName("null environment with fallback secret: rejected")
    void nullEnvironment_withFallbackSecret_shouldBeRejected() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(JwtService.DEV_FALLBACK_SECRET, 86400000L, null));

        assertTrue(ex.getMessage().contains("development fallback JWT secret is only permitted"));
        assertFalse(ex.getMessage().contains(JwtService.DEV_FALLBACK_SECRET));
    }

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production", "uat", "qa", "custom"})
    @DisplayName("any other profile with fallback secret: rejected")
    void otherProfile_withFallbackSecret_shouldBeRejected(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(JwtService.DEV_FALLBACK_SECRET, 86400000L, env));

        assertTrue(ex.getMessage().contains("development fallback JWT secret is only permitted"));
        assertFalse(ex.getMessage().contains(JwtService.DEV_FALLBACK_SECRET));
    }

    @Test
    @DisplayName("valid custom secret under default profile: allowed")
    void defaultProfile_withValidCustomSecret_shouldBeAllowed() {
        MockEnvironment defaultEnv = new MockEnvironment();

        JwtService jwtService = new JwtService(VALID_CUSTOM_SECRET, 86400000L, defaultEnv);

        User user = new User();
        setId(user, 2L);
        user.setEmail("admin@prod.gov.in");
        user.setRole(Role.ADMIN);

        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertEquals("admin@prod.gov.in", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token, "admin@prod.gov.in"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("blank secret under default profile: rejected")
    void defaultProfile_withBlankSecret_shouldBeRejected(String blankSecret) {
        MockEnvironment defaultEnv = new MockEnvironment();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(blankSecret, 86400000L, defaultEnv));

        assertTrue(ex.getMessage().contains("must not be null or blank"));
    }

    @Test
    @DisplayName("null secret under default profile: rejected")
    void defaultProfile_withNullSecret_shouldBeRejected() {
        MockEnvironment defaultEnv = new MockEnvironment();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(null, 86400000L, defaultEnv));

        assertTrue(ex.getMessage().contains("must not be null or blank"));
    }

    @Test
    @DisplayName("weak secret (< 32 bytes): rejected")
    void weakSecret_shouldBeRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        String weakSecret = Base64.getEncoder().encodeToString("too-short!".getBytes());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(weakSecret, 86400000L, env));

        assertTrue(ex.getMessage().contains("at least 256 bits"));
    }

    @Test
    @DisplayName("non-base64 secret: rejected")
    void nonBase64Secret_shouldBeRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new JwtService("not-valid-base64!@#$%", 86400000L, env));

        assertTrue(ex.getMessage().contains("valid Base64-encoded string"));
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
