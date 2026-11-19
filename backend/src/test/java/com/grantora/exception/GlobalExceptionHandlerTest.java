package com.grantora.exception;

import com.grantora.dto.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test-endpoint");
    }

    @Test
    void handleResponseStatusException_preservesStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(
                HttpStatus.CONFLICT, "Application status conflict");

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(ex, request);

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("Application status conflict", response.getBody().message());
        assertEquals("/api/test-endpoint", response.getBody().path());
    }

    @Test
    void handleValidationException_returns400WithFieldErrors() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "email", "Invalid email format"));
        bindingResult.addError(new FieldError("target", "password", "Password must be at least 8 characters"));

        var method = this.getClass().getDeclaredMethod("setUp");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationException(ex, request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Bad Request", response.getBody().error());
        assertNotNull(response.getBody().errors());
        assertEquals("Invalid email format", response.getBody().errors().get("email"));
        assertEquals("Password must be at least 8 characters", response.getBody().errors().get("password"));
    }

    @Test
    void handleAccessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");

        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDenied(ex, request);

        assertEquals(403, response.getStatusCode().value());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().error());
        assertEquals("Forbidden action", response.getBody().message());
    }

    @Test
    void handleAuthenticationException_returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthenticationException(ex, request);

        assertEquals(401, response.getStatusCode().value());
        assertEquals(401, response.getBody().status());
        assertEquals("Unauthorized", response.getBody().error());
    }

    @Test
    void handleNoSuchElement_returns404() {
        NoSuchElementException ex = new NoSuchElementException("Item not found");

        ResponseEntity<ApiErrorResponse> response = handler.handleNoSuchElement(ex, request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
    }

    @Test
    void handleMaxUploadSize_returns413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10485760);

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxUploadSize(ex, request);

        assertEquals(413, response.getStatusCode().value());
        assertEquals(413, response.getBody().status());
        assertEquals("Payload Too Large", response.getBody().error());
    }

    @Test
    void handleGeneralException_returns500WithoutLeakingInternalDetails() {
        RuntimeException ex = new RuntimeException("SELECT * FROM sensitive_db_table failed with SQL syntax error");

        ResponseEntity<ApiErrorResponse> response = handler.handleGeneralException(ex, request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals(500, response.getBody().status());
        assertEquals("Internal Server Error", response.getBody().error());
        assertEquals("An unexpected internal server error occurred. Please try again later.", response.getBody().message());
        assertFalse(response.getBody().message().contains("SQL"));
        assertFalse(response.getBody().message().contains("sensitive_db_table"));
    }
}
