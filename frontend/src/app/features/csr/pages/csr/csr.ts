import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UploadForm } from '../../../../shared/models/csr/UploadForm';
import { AutogenerateForm } from '../../../../shared/models/csr/AutogenerateForm';
import { CaDTO, CsrService, OrganizationDTO } from '../../../../core/services/csr.service';
import { ViewChild, ElementRef } from '@angular/core';

@Component({
  selector: 'app-csr',
  imports: [CommonModule, FormsModule],
  templateUrl: './csr.html',
  styleUrl: './csr.css',
})
export class Csr implements OnInit {

  activeTab: string = 'upload';
  selectedFile: File | null = null;
  loading: boolean = false;

  availableCAs: CaDTO[] = [];
  availableOrganizations: OrganizationDTO[] = [];
  selectedIssuerMaxDate: string = '';

  message = '';
  showMessage = false;
  messageIsError = false;

  uploadForm: UploadForm = {
    issuerSerialNumber: null,
    validTo: null
  };

  autogenerateForm: AutogenerateForm = {
    commonName: null,
    organization: null,
    organizationalUnit: null,
    country: null,
    email: null,
    issuerSerialNumber: null,
    validTo: null,
    keystoreFormat: 'PKCS12',
    keyStorePassword: null,
    alias: null,
    includeSubjectKeyIdentifier: false,
    includeAuthorityKeyIdentifier: false,
    includeExtendedKeyUsage: false
  };


  constructor(private csrService: CsrService,    private cdr: ChangeDetectorRef) {}

  @ViewChild('fileInput') fileInput!: ElementRef;

  ngOnInit(): void {
    this.csrService.getCAs().subscribe({
      next: (cas) => (this.availableCAs = cas),
      error: () => this.showMessageToast('Failed to load CAs', true)
    });

    this.csrService.getOrganizations().subscribe({
      next: (organizations) => (this.availableOrganizations = organizations),
      error: () => this.showMessageToast('Failed to load organizations', true)
    });
  }

  onFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (file) {
      this.selectedFile = file;
    }
  }

  onIssuerChange(): void {
    const serialNumber = this.activeTab === 'upload'
      ? this.uploadForm.issuerSerialNumber
      : this.autogenerateForm.issuerSerialNumber;
    const ca = this.availableCAs.find((c) => c.serialNumber === serialNumber);
    this.selectedIssuerMaxDate = ca ? ca.validTo.substring(0, 10) : '';
  }

  uploadCsr(): void {
    if (!this.selectedFile) {
      this.showMessageToast('Please select a CSR file.', true); return;
    }
    if (!this.uploadForm.issuerSerialNumber) {
      this.showMessageToast('Please select an issuer CA.', true); return;
    }
    if (!this.uploadForm.validTo) {
      this.showMessageToast('Please select a valid-to date.', true); return;
    }

    const validToMs = new Date(this.uploadForm.validTo).getTime();
    this.loading = true;

    this.csrService.uploadCsr(this.selectedFile, this.uploadForm.issuerSerialNumber, validToMs).subscribe({
      next: () => {
        this.loading = false;
        this.showMessageToast('Certificate issued successfully!', false);
          this.resetUploadForm();
      },
      error: (err) => {
        this.loading = false;
        this.showMessageToast(err.error ?? 'Failed to issue certificate.', true);
      },
    });
  }

  autogenerate(): void {
    if (!this.autogenerateForm.commonName) {
      this.showMessageToast('Common Name is required.', true); return;
    }
    if (!this.autogenerateForm.organization) {
      this.showMessageToast('Organization is required.', true); return;
    }
    if (!this.autogenerateForm.issuerSerialNumber) {
      this.showMessageToast('Please select an issuer CA.', true); return;
    }
    if (!this.autogenerateForm.validTo) {
      this.showMessageToast('Please select a valid-to date.', true); return;
    }
    if (!this.autogenerateForm.keyStorePassword) {
      this.showMessageToast('Keystore password is required.', true); return;
    }

    this.loading = true;

    const payload = {
      commonName: this.autogenerateForm.commonName!,
      organization: this.autogenerateForm.organization!,
      organizationalUnit: this.autogenerateForm.organizationalUnit ?? undefined,
      country: this.autogenerateForm.country ?? undefined,
      email: this.autogenerateForm.email ?? undefined,
      issuerSerialNumber: this.autogenerateForm.issuerSerialNumber!,
      keyStorePassword: this.autogenerateForm.keyStorePassword!,
      alias: this.autogenerateForm.alias ?? undefined,
      validTo: new Date(this.autogenerateForm.validTo!),
      includeSubjectKeyIdentifier: this.autogenerateForm.includeSubjectKeyIdentifier,
      includeAuthorityKeyIdentifier: this.autogenerateForm.includeAuthorityKeyIdentifier,
      includeExtendedKeyUsage: this.autogenerateForm.includeExtendedKeyUsage,
    };

    this.csrService.autogenerate(payload).subscribe({
      next: (blob: Blob) => {
        this.loading = false;
        this.showMessageToast('Keystore generated — download starting…', false);
        this.resetAutoForm();
        setTimeout(() => {
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `cert_${Date.now()}.p12`;
          a.click();
          window.URL.revokeObjectURL(url);
        });
      },
      error: (err) => {
        this.loading = false;
        if (err.error instanceof Blob) {
          err.error.text().then((text: string) => {
            this.showMessageToast(text || 'Failed to generate certificate.', true);
          });
        } else {
          this.showMessageToast(err.error ?? 'Failed to generate certificate.', true);
        }
      }
    });
  }

  resetUploadForm() {
    this.uploadForm = { issuerSerialNumber: null, validTo: null };
    this.selectedFile = null;
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  resetAutoForm() {
    this.autogenerateForm = {
      commonName: null,
      organization: null,
      organizationalUnit: null,
      country: null,
      email: null,
      issuerSerialNumber: null,
      validTo: null,
      keystoreFormat: 'PKCS12',
      keyStorePassword: null,
      alias: null,
      includeSubjectKeyIdentifier: false,
      includeAuthorityKeyIdentifier: false,
      includeExtendedKeyUsage: false
    };
  }

  showMessageToast(msg: string, isError: boolean = false): void {
    this.message = msg;
    this.messageIsError = isError;
    this.showMessage = true;
    this.cdr.detectChanges(); 
    setTimeout(() => { this.showMessage = false; }, 3500);
  }
}