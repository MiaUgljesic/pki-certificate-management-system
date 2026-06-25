package com.ib.pki.dto.response.csr;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutogenerateCertificateSigningResponse {
    String serialNumber;
    byte[] certificateBytes;

}
