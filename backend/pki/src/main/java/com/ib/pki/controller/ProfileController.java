package com.ib.pki.controller;

import com.ib.pki.dto.request.profile.VerifyTwoFactorRequest;
import com.ib.pki.dto.response.profile.QrDTO;
import com.ib.pki.dto.response.profile.UserProfileDTO;
import com.ib.pki.model.User;
import com.ib.pki.repository.UserRepository;
import com.ib.pki.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;

    public ProfileController(ProfileService profileService, UserRepository userRepository) {
        this.profileService = profileService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserProfileDTO> getProfile() {
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if(optionalUser.isEmpty()){
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        UserProfileDTO dto = profileService.getProfileInformation(user);
        return ResponseEntity.ok(dto);
    }


    @PostMapping("/2fa/generate")
    public ResponseEntity<QrDTO> generateQrCode() {
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        QrDTO qrDTO = profileService.generate2FAQr(user);
        return ResponseEntity.ok(qrDTO);
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<Void> verifyTwoFactor(@RequestBody VerifyTwoFactorRequest request) {
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        profileService.verify2FA(user, request.getCode());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disableTwoFactor() {
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if(optionalUser.isEmpty()){
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        profileService.disable2FA(user);
        return ResponseEntity.ok().build();
    }
}