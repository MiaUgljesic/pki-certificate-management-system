export interface AutogenerateForm {
  commonName: string | null;
  organization: string | null;
  organizationalUnit: string | null;
  country: string | null;
  email: string | null;

  issuerSerialNumber: string | null;
  validTo: string | null;

  keystoreFormat: string;
  keyStorePassword: string | null;
  alias: string | null;

  includeSubjectKeyIdentifier: boolean;
  includeAuthorityKeyIdentifier: boolean;
  includeExtendedKeyUsage: boolean;
}