package com.ib.pki.controller;

import com.ib.pki.dto.request.auth.*;
import com.ib.pki.dto.response.auth.LoginResponse;
import com.ib.pki.dto.response.auth.ActivationResponseDTO;
import com.ib.pki.dto.response.auth.RefreshTokenResponse;
import com.ib.pki.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/login/2fa")
    public ResponseEntity<LoginResponse> loginWith2FA(@Valid @RequestBody TwoFactorLoginRequest request) {
        return ResponseEntity.ok(authService.loginWith2FA(request));
    }

    @PostMapping("/activate")
    public ResponseEntity<ActivationResponseDTO> activateCAUser(@Valid @RequestBody ActivationRequestDTO request) {
        return ResponseEntity.ok(authService.activateCAUser(request.getToken(), request.getPassword()));
    }

    @PostMapping("/activate-challenge")
    public ResponseEntity<String> activateUser(@Valid @RequestBody ActivationChallengeRequestDTO dto) {
        try {
            authService.activateUser(dto.getToken(), dto.getDecryptedChallenge());
            return ResponseEntity.ok("Account activated successfully! You can now log in.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred during activation: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegistrationRequestDTO request) {
        try {
            authService.registerUser(request);
            return ResponseEntity.ok("Registration initiated. Please check your email for the activation challenge.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred during registration: " + e.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) throws Exception {
        authService.initiatePasswordReset(request.getEmail());
        return ResponseEntity.ok("Password reset challenge sent to your email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        boolean success = authService.completePasswordReset(request);
        if (success) {
            return ResponseEntity.ok("Password reset successfully! You can now log in.");
        }
        return ResponseEntity.status(400).body("Invalid challenge resolution or expired token.");
    }
}
