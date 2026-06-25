package com.ib.pki.controller;

import com.ib.pki.dto.request.certificate.CertificateDownloadRequest;
import com.ib.pki.dto.request.certificate.RevocationRequestDTO;
import com.ib.pki.dto.response.certificate.*;
import com.ib.pki.dto.request.certificate.CertificateRequestDTO;
import com.ib.pki.model.Certificate;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.CertificateFormat;
import com.ib.pki.repository.UserRepository;
import com.ib.pki.service.CertificateDownloadService;
import com.ib.pki.service.CertificateRevocationService;
import com.ib.pki.service.CertificateService;
import com.ib.pki.service.KeyStoreService;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final UserRepository userRepository;
    private final KeyStoreService keyStoreService;
    private final CertificateDownloadService certificateDownloadService;
    private final CertificateRevocationService revocationService;


    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CertificateResponseDTO>> getAllCertificates(
            @PageableDefault(size = 20, sort = "validFrom", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) @Min(0) Long date)
    {
        Page<CertificateResponseDTO> certificates = certificateService.getAllCertificates(pageable, date);
        return ResponseEntity.ok(certificates);
    }

    @GetMapping("/organization")
    @PreAuthorize("hasRole('CA_USER')")
    public ResponseEntity<Page<CertificateResponseDTO>> getOrganizationCertificates(
            @PageableDefault(size = 20, sort = "validFrom", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) @Min(0) Long date)
    {
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        Page<CertificateResponseDTO> certificates = certificateService.getOrganizationCertificates(user, pageable, date);
        return ResponseEntity.ok(certificates);
    }

    @PostMapping("/issue")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CA_USER')")
    public ResponseEntity<?> issueCertificate(@Valid @RequestBody CertificateRequestDTO dto) {
        try {
            certificateService.issueCertificate(dto);
            return ResponseEntity.ok("Certificate successfully issued.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/user-overview")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<CertificateOverviewDTO>> getUserCertificates(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                                                            @RequestParam(defaultValue = "date") String sortBy, @RequestParam(defaultValue = "desc") String sortDir,
                                                                            @RequestParam(required = false) @Min(0) Long date) {
        UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> optionalUser = userRepository.findByEmail(details.getUsername());
        if(optionalUser.isEmpty()){
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        Page<CertificateOverviewDTO> overview;
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), mapSortField(sortBy));
        Pageable pageable = PageRequest.of(page, size, sort);
        overview = certificateService.getUserCertificates(user, pageable, date);
        return ResponseEntity.ok(overview);
    }

    @PostMapping("/download")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CA_USER')")
    public ResponseEntity<ByteArrayResource> downloadCertificate(@Valid @RequestBody CertificateDownloadRequest request) {
        // Generisanje bajtova fajla
        byte[] keyStoreBytes = keyStoreService.generateDownloadableKeyStore(request);
        ByteArrayResource resource = new ByteArrayResource(keyStoreBytes);

        // Dinamičko ime fajla (npr. cert_12345.p12)
        String fileName = "cert_" + request.getSerialNumber() + ".p12";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/x-pkcs12"))
                .contentLength(keyStoreBytes.length)
                .body(resource);
    }


    @GetMapping("/download/{serialNumber}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CA_USER') or hasRole('USER')")
    public ResponseEntity<ByteArrayResource> downloadCertificateWithoutKey(@PathVariable String serialNumber,
                                                                           @RequestParam(defaultValue = "PEM") CertificateFormat format) {
        byte[] data = certificateDownloadService.certificateDownload(serialNumber, format);
        String extension = format == CertificateFormat.PEM ? ".pem" : ".cer";
        String mediaType = format == CertificateFormat.PEM ? "application/x-pem-file" : "application/pkix-cert";
        String fileName = "cert_" + serialNumber + extension;

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(mediaType)).body(new ByteArrayResource(data));
    }

        private String mapSortField(String sortBy) {
        return switch (sortBy) {
            case "commonName" -> "commonName";
            case "organization" -> "organizationName";
            case "status" -> "status";
            case "validFrom" -> "validFrom";
            case "validTo" -> "validTo";
            default -> "serialNumber";
        };
    }

    @PostMapping("/revoke")
    @PreAuthorize("hasAnyRole('ADMIN', 'CA_USER', 'USER')")
    public ResponseEntity<?> revokeCertificate(@Valid @RequestBody RevocationRequestDTO request) {
        try {
            UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User loggedInUser = userRepository.findByEmail(details.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            CertificateResponseDTO revokedCert = revocationService.revokeCertificate(request.getSerialNumber(), request.getReason(), loggedInUser);


            return ResponseEntity.ok(revokedCert);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/unrevoke/{serialNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CA_USER', 'USER')")
    public ResponseEntity<String> unrevokeCertificate(@PathVariable String serialNumber) {
        try {
            UserDetails details = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User loggedInUser = userRepository.findByEmail(details.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            revocationService.unrevokeCertificate(serialNumber, loggedInUser);
            return ResponseEntity.ok("The certificate has been successfully removed from suspension and reinstated.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @GetMapping("/crl")
    public ResponseEntity<List<CrlItemResponseDTO>> getCertificateRevocationList() {
        try {
            List<CrlItemResponseDTO> crlList = revocationService.getCertificateRevocationList();
            return ResponseEntity.ok(crlList);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/crl/{caSerialNumber}.crl")
    public ResponseEntity<byte[]> downloadCRL(@PathVariable String caSerialNumber) {
        try {
            byte[] crlBytes = revocationService.generateX509Crl(caSerialNumber);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/pkix-crl"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + caSerialNumber + ".crl\"")
                    .body(crlBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Retrieves a list of all active CA certificates (Root and Intermediate)
     * containing only essential identification fields.
     */
    @GetMapping("/signing-authorities")
    @PreAuthorize("hasAnyRole('ADMIN', 'CA_USER', 'USER')")
    public ResponseEntity<List<CaSuggestionResponseDTO>> getActiveSigningAuthorities() {
        try {
            List<CaSuggestionResponseDTO> authorities = certificateService.getActiveSigningAuthorities();
            return ResponseEntity.ok(authorities);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Resolves the CRL for a certificate, downloads it live, and checks its validity status.
     */
    @GetMapping("/verify-status/{serialNumber}")
    public ResponseEntity<?> verifyCertificateStatus(@PathVariable String serialNumber) {
        try {
            CertificateStatusCheckResponseDTO statusReport = revocationService.verifyCertificateInCrl(serialNumber);
            return ResponseEntity.ok(statusReport);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Verification execution crashed: " + e.getMessage());
        }
    }

}
