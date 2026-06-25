import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CertificateService, RevocationReason } from '../../../core/services/certificate.service';
import { Certificate } from '../../models/certificate-overview/Certificates';
import { ToastService } from '../../services/toast.service';
import { Button } from '../button/button';

@Component({
  selector: 'app-revoke-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, Button],
  templateUrl: './revoke-modal.component.html',
  styleUrls: ['./revoke-modal.component.css']
})
export class RevokeModalComponent {
  @Input() serialNumber!: string;
  @Output() closed = new EventEmitter<Certificate | null>();

  // Exclude REMOVE_FROM_CRL from the UI list of reasons
  revocationReasons = (Object.values(RevocationReason) as string[]).filter(r => r !== RevocationReason.REMOVE_FROM_CRL);
  selectedReason: string = RevocationReason.UNSPECIFIED;
  loading = false;

  constructor(private certificateService: CertificateService, private toastService: ToastService) {}

  onConfirm(): void {
    if (!this.serialNumber) {
      this.toastService.error('Missing serial number');
      return;
    }

    this.loading = true;
    this.certificateService.revokeCertificate(this.serialNumber, this.selectedReason)
      .subscribe({
        next: (updated: Certificate) => {
          this.toastService.success('Certificate revoked');
          this.loading = false;
          this.closed.emit(updated);
        },
        error: (err) => {
          const message = err?.error || err?.message || 'Failed to revoke certificate';
          this.toastService.error(message);
          this.loading = false;
        }
      });
  }

  onCancel(): void {
    this.closed.emit(null);
  }
}
