package com.ib.pki.dto.response.certificate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaSuggestionResponseDTO {
    private String serialNumber;
    private String commonName;
    private String organizationName;
}