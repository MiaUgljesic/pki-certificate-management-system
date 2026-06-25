package com.ib.pki.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "certificate_extensions")
@Getter @Setter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private Certificate certificate;

    @Column(nullable = false)
    private String extensionOid;

    @Column(columnDefinition = "TEXT")
    private String extensionValue;

    @Builder.Default
    @Column(nullable = false)
    private boolean critical = false;
}
