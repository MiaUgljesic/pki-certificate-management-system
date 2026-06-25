import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Certificate } from '../../../../shared/models/certificate-overview/Certificates';
import { RevokeModalComponent } from '../../../../shared/components/revoke-modal/revoke-modal.component';
import { Button } from '../../../../shared/components/button/button';
import { CertificateService } from '../../../../core/services/certificate.service';
import { AuthService } from '../../../../core/services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-certificate-details',
  standalone: true,
  imports: [CommonModule,RevokeModalComponent,Button],
  templateUrl: './certificate-details.html',
  styleUrl: './certificate-details.css',
})
export class CertificateDetails implements OnInit {
  certificate: Certificate | null = null;
  showRevokeModal: boolean = false;

  constructor(private route: ActivatedRoute, private router: Router, private authService: AuthService, private certificateService: CertificateService) {}

  ngOnInit(): void {
    const state = history.state;
    if (state && state['certificate']) {
      this.certificate = state['certificate'];
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'badge bg-success';
      case 'REVOKED': return 'badge bg-danger';
      case 'EXPIRED': return 'badge bg-secondary';
      default: return 'badge bg-secondary';
    }
  }

  openRevokeModal(): void {
    this.showRevokeModal = true;
  }

  private normalizeRevocationReason(reason: string | null | undefined): string {
    if (!reason) return '';
    return reason.toString().toUpperCase().replace(/\s+/g, '_');
  }

  canUnrevoke(): boolean {
    const isRevoked = this.certificate?.status === 'REVOKED';
    const normalized = this.normalizeRevocationReason(this.certificate?.revocationReason);
    return isRevoked && (normalized === 'CERTIFICATE_HOLD' || normalized === 'CERTIFICATE_HOLD');
  }

  unrevokeCertificate(): void {
    if (!this.certificate) {
      return;
    }
    this.certificateService.unrevokeCertificate(this.certificate.serialNumber).subscribe({
      next: () => {
        if (this.certificate) {
          this.certificate.status = 'ACTIVE';
          this.certificate.revocationReason = undefined as any;
          this.certificate.revokedAt = undefined as any;
        }
      },
      error: (err) => console.error('Error unrevoke certificate:', err)
    });
  }

  handleModalClose(updated: any | null): void {
    this.showRevokeModal = false;
    if (updated && typeof updated === 'object') {
      const cert = updated as Certificate;
      try {
        if (cert.validFrom) cert.validFrom = new Date(cert.validFrom).toLocaleDateString('sr-RS');
        if (cert.validTo) cert.validTo = new Date(cert.validTo).toLocaleDateString('sr-RS');
        if (cert.revokedAt) cert.revokedAt = new Date(cert.revokedAt).toLocaleDateString('sr-RS');
      } catch (e) {
        // keep original values on parse error
      }
      this.certificate = cert;
    }
  }

  canDownloadCrl(): boolean {
    if (!this.certificate) return false;
    // Never allow CRL download for end-entity certificates
    if (this.certificate.certificateType === 'END_ENTITY') return false;
    const role = this.authService.getUserRole();
    return (role === 'ADMIN' || role === 'CA_USER') || (this.certificate.certificateType === 'ROOT' || this.certificate.certificateType === 'INTERMEDIATE');
  }

  goBack(): void {
    this.router.navigate(['/user-certificate-overview']);
  }

  downloadCrl(): void {
    if (!this.certificate) {
      return;
    }
    this.certificateService.downloadCRL(this.certificate.serialNumber).subscribe({
      next: (response) => {
        const blob = response.body ?? new Blob();
        const fileName = this.resolveFileName(response.headers.get('content-disposition'), this.certificate?.serialNumber ?? '', 'crl');
        this.triggerDownload(blob, fileName);
      },
      error: (err) => console.error('Error downloading CRL:', err)
    });
  }

  private resolveFileName(contentDisposition: string | null, serialNumber: string, format : string): string {
    if (contentDisposition) {
      const match = /filename="?([^";]+)"?/i.exec(contentDisposition);
      if (match?.[1]) {
        return match[1];
      }
    }
    return `crl_${serialNumber}.${format}`;
  }

  private triggerDownload(blob: Blob, fileName: string): void {
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    window.URL.revokeObjectURL(url);
  }
}