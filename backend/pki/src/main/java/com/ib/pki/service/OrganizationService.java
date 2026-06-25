package com.ib.pki.service;

import com.ib.pki.dto.response.organization.OrganizationResponseDTO;
import com.ib.pki.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public List<OrganizationResponseDTO> getAllOrganizations() {
        return organizationRepository.findAll()
                .stream()
                .map(org -> new OrganizationResponseDTO(org.getId(), org.getName()))
                .collect(Collectors.toList());
    }
}
