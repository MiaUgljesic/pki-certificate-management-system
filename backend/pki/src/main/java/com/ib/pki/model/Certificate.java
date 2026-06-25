package com.ib.pki.model;

import com.ib.pki.model.enums.CertificateStatus;
import com.ib.pki.model.enums.CertificateType;
import com.ib.pki.model.enums.RevocationReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "certificates", indexes = {
        @Index(name = "idx_certificate_serial", columnList = "serialNumber"),
        @Index(name = "idx_certificate_status", columnList = "status"),
        @Index(name = "idx_certificate_organization", columnList = "organization_id")
})
@Getter @Setter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false)
    private String commonName;

    private String organizationName;

    private String organizationalUnit;

    @Column(length = 2)
    private String country;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificateType type;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificateStatus status = CertificateStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validTo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String certificateData;

    @Column(columnDefinition = "TEXT")
    private String privateKeyEncrypted;

    @Enumerated(EnumType.STRING)
    private RevocationReason revocationReason;

    private LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_id")
    private Certificate issuer;

    private String keyAlgorithm;

    private Integer keySize;

    private String signatureAlgorithm;

    @Builder.Default
    @OneToMany(mappedBy = "certificate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CertificateExtension> extensions = new ArrayList<>();
}
