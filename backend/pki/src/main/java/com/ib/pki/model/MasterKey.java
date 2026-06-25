package com.ib.pki.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "master_keys")
@Getter @Setter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer version;

    @Column(name = "encrypted_key_data", nullable = false, columnDefinition = "TEXT")
    private String encryptedKeyData;

    @Column(nullable = false)
    private String salt;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime rotatedAt;
}