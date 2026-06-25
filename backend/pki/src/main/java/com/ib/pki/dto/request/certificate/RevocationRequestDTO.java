package com.ib.pki.dto.request.certificate;
import com.ib.pki.model.enums.RevocationReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevocationRequestDTO {
    @NotBlank(message = "Serial number is required")
    private String serialNumber;

    @NotNull(message = "Revocation reason is required")
    private RevocationReason reason;
}
