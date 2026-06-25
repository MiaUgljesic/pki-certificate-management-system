package com.ib.pki.service;

import com.ib.pki.dto.request.admin.RegistrationRequestDTO;
import com.ib.pki.dto.response.admin.RegistrationResponseDTO;
import com.ib.pki.model.ActivationToken;
import com.ib.pki.model.Organization;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.TokenType;
import com.ib.pki.model.enums.UserRole;
import com.ib.pki.repository.ActivationTokenRepository;
import com.ib.pki.repository.OrganizationRepository;
import com.ib.pki.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationKeyService keyService;
    private final ActivationTokenRepository activationTokenRepository;

    @Transactional
    public RegistrationResponseDTO registerCAUser(RegistrationRequestDTO request) {

        // find organization
        Optional<Organization> found = organizationRepository.findByName(request.getOrganizationName());
        Organization org;
        if  (found.isPresent()) {
            org = found.get();
        }else {
            // creating organization
            org = Organization.builder()
                    .name(request.getOrganizationName())
                    .build();

            org = organizationRepository.save(org);

            keyService.createOrganizationKey(org);
        }

        // validate email

        Optional<User> existing = userRepository.findByEmail(request.getUserEmail());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        // creating CA User
        User user = User.builder()
                .email(request.getUserEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .organization(org)
                .active(false)
                .role(UserRole.CA_USER)
                .passwordHash("TEMPORARY_HASH")
                .build();

        userRepository.save(user);

        // sending activation email
        sendActivationEmail(user);

        RegistrationResponseDTO response = new RegistrationResponseDTO();
        response.setUserEmail(user.getEmail());
        response.setOrganizationName(org.getName());
        response.setMessage("Activation email sent successfully to " + user.getEmail() +" .");

        return response;
    }

    public void sendActivationEmail(User user) {
        // delete old tokens
        activationTokenRepository.findByUser(user).ifPresent(activationTokenRepository::delete);

        // creating new registration token
        ActivationToken token = ActivationToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .type(TokenType.REGISTRATION)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        activationTokenRepository.save(token);
        emailService.sendActivationMail(user.getEmail(), token);
    }
}