package com.ib.pki.controller;

import com.ib.pki.dto.request.admin.RegistrationRequestDTO;
import com.ib.pki.dto.response.admin.RegistrationResponseDTO;
import com.ib.pki.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/register-ca")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistrationResponseDTO> registerCAUser(@Valid @RequestBody RegistrationRequestDTO request) {
        return ResponseEntity.ok(adminService.registerCAUser(request));
    }
}
