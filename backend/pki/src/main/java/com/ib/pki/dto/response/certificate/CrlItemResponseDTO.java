package com.ib.pki.dto.response.certificate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrlItemResponseDTO {
    private String serialNumber;
    private String reason;
    private String revokedAt;
}