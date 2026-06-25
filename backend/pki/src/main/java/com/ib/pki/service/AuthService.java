package com.ib.pki.service;


import com.ib.pki.dto.request.auth.LoginRequest;
import com.ib.pki.dto.request.auth.RegistrationRequestDTO;
import com.ib.pki.dto.request.auth.ResetPasswordRequestDTO;
import com.ib.pki.dto.request.auth.TwoFactorLoginRequest;
import com.ib.pki.dto.response.auth.LoginResponse;
import com.ib.pki.dto.response.auth.ActivationResponseDTO;
import com.ib.pki.dto.response.auth.RefreshTokenResponse;
import com.ib.pki.model.*;
import com.ib.pki.model.ActivationToken;
import com.ib.pki.model.RefreshToken;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.UserRole;
import com.ib.pki.model.enums.TokenType;
import com.ib.pki.repository.ActivationTokenRepository;
import com.ib.pki.repository.OrganizationRepository;
import com.ib.pki.repository.RecoverTokenRepository;
import com.ib.pki.repository.UserRepository;
import com.ib.pki.security.jwt.JwtService;
import com.ib.pki.util.CryptoUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ActivationTokenRepository activationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final OrganizationRepository  organizationRepository;
    private final RecoverTokenRepository  recoverTokenRepository;
    private final TwoFactorAuthService twoFactorAuthService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if(user.isTwoFactorEnabled()){
            return new LoginResponse(null,null, null, user.getRole(), null, true);
        }

        String token = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        String organizationName = null;
        if (user.getRole() == UserRole.CA_USER && user.getOrganization() != null) {
            organizationName = user.getOrganization().getName();
        }

        return new LoginResponse(
            token,
            refreshToken.getToken(),
            "Bearer",
            user.getRole(),
            organizationName,
            false
        );
    }

    public LoginResponse loginWith2FA(TwoFactorLoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());
        if(optionalUser.isEmpty()){
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = optionalUser.get();
        boolean valid = twoFactorAuthService.verifyCode(user, Integer.parseInt(request.getCode()));
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid 2FA code");
        }

        String token = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        String organizationName = null;
        if (user.getRole() == UserRole.CA_USER && user.getOrganization() != null) {
            organizationName = user.getOrganization().getName();
        }
        return new LoginResponse(token, refreshToken.getToken(), "Bearer", user.getRole(), organizationName, false);
    }

        @Transactional
        public RefreshTokenResponse refreshToken(String refreshTokenValue) {
            RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenValue);

            if (refreshToken.isRevoked()) {
                refreshTokenService.revokeAll(refreshToken.getUser());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
            }

            refreshTokenService.ensureNotExpired(refreshToken);

        RefreshToken rotatedToken = refreshTokenService.rotateRefreshToken(refreshToken);
        String accessToken = jwtService.generateToken(refreshToken.getUser());

        return new RefreshTokenResponse(
            accessToken,
            rotatedToken.getToken(),
                "Bearer"
        );
        }

    @Transactional
    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        refreshTokenService.revokeAll(user);
    }

    @Transactional
    public ActivationResponseDTO activateCAUser(String token, String password) {

        // token validation
        ActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid activation token"));

        if (activationToken.isUsed() ||
                activationToken.getExpiresAt().isBefore(LocalDateTime.now()) ||
                !activationToken.getType().equals(TokenType.REGISTRATION)) {
            throw new IllegalStateException("Activation token is invalid, expired, or wrong type");
        }

        User user = activationToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        userRepository.save(user);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        ActivationResponseDTO response = new ActivationResponseDTO();
        response.setEmail(user.getEmail());
        response.setMessage("Account activated successfully.");
        return response;
    }

    @Transactional
    public ActivationResponseDTO activateUser(String token, String decryptedChallenge) throws Exception {
        // token validation
        ActivationToken tokenEntity = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid activation token"));

        if (tokenEntity.isUsed() ||
                tokenEntity.getExpiresAt().isBefore(LocalDateTime.now()) ||
                !tokenEntity.getType().equals(TokenType.REGISTRATION)) {
            throw new IllegalStateException("Activation token is invalid, expired, or wrong type");
        }

        // Challenge Verification
        if (!tokenEntity.getOriginalChallenge().equals(decryptedChallenge)) {
            throw new IllegalArgumentException("Cryptographic challenge verification failed. Invalid private key.");
        }


        User user = tokenEntity.getUser();
        user.setActive(true);
        userRepository.save(user);

        tokenEntity.setUsed(true);
        activationTokenRepository.save(tokenEntity);

        ActivationResponseDTO response = new ActivationResponseDTO();
        response.setEmail(user.getEmail());
        response.setMessage("Account activated successfully.");
        return response;
    }

    @Transactional
    public void registerUser(RegistrationRequestDTO dto) throws Exception {

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use.");
        }

        Organization organization = organizationRepository.findByName(dto.getOrganization()).orElseThrow(() -> new IllegalArgumentException("Organization not found: " + dto.getOrganization()));

        // creating inactive user
        User user = User.builder()
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .organization(organization)
                .publicKey(dto.getPublicKeyPem()) // saving public key
                .active(false)
                .build();

        userRepository.save(user);

        sendActivationEmailWithChallenge(user);
    }

    public void sendActivationEmailWithChallenge(User user) throws Exception {
        // delete old tokens
        activationTokenRepository.findByUser(user).ifPresent(activationTokenRepository::delete);

        // generating challenge
        String originalChallenge = UUID.randomUUID().toString(); // Random string

        // convert text to PublicKey and encrypt
        var publicKey = CryptoUtil.convertPemToPublicKey(user.getPublicKey());
        String encryptedChallengeBase64 = CryptoUtil.encryptWithPublicKey(originalChallenge, publicKey);

        // creating activation token
        ActivationToken token = ActivationToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .type(TokenType.REGISTRATION)
                .expiresAt(LocalDateTime.now().plusHours(2)) // 2 hours
                .used(false)
                .originalChallenge(originalChallenge)
                .encryptedChallengeBase64(encryptedChallengeBase64)
                .build();

        activationTokenRepository.save(token);

        emailService.sendActivationMailWithChallenge(user.getEmail(), token);
    }

    public void initiatePasswordReset(String email) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String originalChallenge = UUID.randomUUID().toString();

        var publicKey = CryptoUtil.convertPemToPublicKey(user.getPublicKey());
        String encryptedChallengeBase64 = CryptoUtil.encryptWithPublicKey(originalChallenge, publicKey);

        RecoverToken token = RecoverToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(30)) // 30 minutes
                .used(false)
                .originalChallenge(originalChallenge)
                .encryptedChallengeBase64(encryptedChallengeBase64)
                .build();

        recoverTokenRepository.save(token);

        emailService.sendPasswordResetMail(user.getEmail(), token);
    }

    public boolean completePasswordReset(ResetPasswordRequestDTO request) {
        RecoverToken token = recoverTokenRepository.findByToken(request.getToken())
                .orElse(null);

        if (token == null || token.isUsed()) {
            return false;
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        if (!token.getOriginalChallenge().equals(request.getDecryptedChallenge())) {
            return false;
        }

        User user = token.getUser();

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsed(true);
        recoverTokenRepository.save(token);

        return true;
    }
}
