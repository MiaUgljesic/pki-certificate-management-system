package com.ib.pki.service;

import com.ib.pki.dto.response.profile.QrDTO;
import com.ib.pki.dto.response.profile.UserProfileDTO;
import com.ib.pki.model.User;
import com.ib.pki.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final TwoFactorAuthService twoFactorAuthService;

    public ProfileService(UserRepository userRepository, TwoFactorAuthService twoFactorAuthService) {
        this.userRepository = userRepository;
        this.twoFactorAuthService = twoFactorAuthService;
    }

    public UserProfileDTO getProfileInformation(User user) {
        return UserProfileDTO.builder().email(user.getEmail())
                .name(user.getFirstName()).surname(user.getLastName())
                .organization(user.getOrganization().getName())
                .publicKey(user.getPublicKey()).isTwoFactorEnabled(user.isTwoFactorEnabled()).build();
    }

    public void verify2FA(User user, String code) {
        try {
            int parsedCode = Integer.parseInt(code);
            boolean isValid = twoFactorAuthService.verifyCode(user, parsedCode);

            if (!isValid) {
                throw new RuntimeException("Invalid 2FA code. Please try again.");
            }
            user.setTwoFactorEnabled(true);
            userRepository.save(user);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Verification code must be a 6-digit number");
        }
    }

    public QrDTO generate2FAQr(User user) {
        String qrBase64Url = twoFactorAuthService.generateQRUrl(user);
        userRepository.save(user);
        return new QrDTO(qrBase64Url);
    }

    public void disable2FA(User user){
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
    }
}