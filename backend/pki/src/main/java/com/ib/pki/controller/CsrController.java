package com.ib.pki.controller;

import com.ib.pki.dto.request.csr.AutogenerateCertificateSigningRequest;
import com.ib.pki.dto.response.csr.AutogenerateCertificateSigningResponse;
import com.ib.pki.dto.response.csr.CaDTO;
import com.ib.pki.dto.response.csr.OrganizationDTO;
import com.ib.pki.model.User;
import com.ib.pki.repository.UserRepository;
import com.ib.pki.service.CsrService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/csrs")
public class CsrController {

    private final UserRepository userRepository;
    private final CsrService csrService;

    public CsrController(CsrService csrService, UserRepository userRepository) {
        this.csrService = csrService;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?>  uploadCsr(@RequestParam MultipartFile csrFile, @RequestParam String issuerSerialNumber, @RequestParam Long validTo){
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if(optionalUser.isEmpty()){
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        try {
            Date parsedValidTo = new Date(validTo);
            csrService.issueCertificateFromCsr(csrFile.getBytes(), issuerSerialNumber, parsedValidTo, user);
            return ResponseEntity.ok("Certificate issued successfully");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Could not read CSR file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error issuing certificate: " + e.getMessage());
        }
    }

    @PostMapping("/autogenerate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> autogenerateCsr(@Valid @RequestBody AutogenerateCertificateSigningRequest request) {
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if(optionalUser.isEmpty()){
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        try{
            AutogenerateCertificateSigningResponse response = csrService.autogenerateCertificate(request, user);
            ByteArrayResource resource = new ByteArrayResource(response.getCertificateBytes());
            String fileName = "cert_" + response.getSerialNumber() + ".p12";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("application/x-pkcs12"))
                    .contentLength(response.getCertificateBytes().length)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error generating certificate: " + e.getMessage());
        }
    }

    @GetMapping("/issuers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CaDTO>> getAvailableIssuers() {
        return ResponseEntity.ok(csrService.getAvailableIssuers());
    }

    @GetMapping("/organizations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<OrganizationDTO>> getOrganizations() {
        return ResponseEntity.ok(csrService.getOrganizations());
    }
}
