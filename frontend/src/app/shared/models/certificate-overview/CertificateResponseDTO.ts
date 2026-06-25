export interface CertificateResponseDTO {
  id: number;
  serialNumber: string;
  commonName: string;
  organizationName: string;
  organizationalUnit: string | null;
  country: string | null;
  email: string | null;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  validFrom: string;
  validTo: string;
  keyAlgorithm: string | null;
  keySize: number | null;
  signatureAlgorithm: string | null;
  revocationReason: string | null;
  revokedAt: string | null;
  issuerSerialNumber: string | null;
  certificateType: string;
  SAN?: string | null;
  includeSubjectKeyIdentifier?: boolean;
  includeAuthorityKeyIdentifier?: boolean;
  includeExtendedKeyUsage?: boolean;
  hasPrivateKey : boolean
}
