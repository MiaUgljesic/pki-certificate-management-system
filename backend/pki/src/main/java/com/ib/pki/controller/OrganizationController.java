package com.ib.pki.controller;

import com.ib.pki.dto.response.organization.OrganizationResponseDTO;
import com.ib.pki.repository.OrganizationRepository;
import com.ib.pki.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/all")
    public ResponseEntity<List<OrganizationResponseDTO>> getOrganizations() {
        return ResponseEntity.ok(organizationService.getAllOrganizations());
    }
}
