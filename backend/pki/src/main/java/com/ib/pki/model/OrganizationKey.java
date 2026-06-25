package com.ib.pki.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "organization_keys")
@Getter @Setter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "master_key_id", nullable = false)
    private MasterKey masterKey;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedKeyData;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
