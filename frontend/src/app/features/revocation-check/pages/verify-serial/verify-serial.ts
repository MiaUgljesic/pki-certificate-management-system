import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CertificateService } from '../../../../core/services/certificate.service';

@Component({
  selector: 'app-verify-serial',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './verify-serial.html',
  styleUrl: './verify-serial.css'
})
export class VerifySerialComponent {
  serialNumber = signal('');
  loading = signal(false);
  result: any = null;
  error = signal('');
  formattedMessage = signal('');
  crlUrl: string | null = null;

  constructor(private certificateService: CertificateService) {}

  onSerialChange(value: string): void {
    this.serialNumber.set(value);
  }

  verify(): void {
    const sn = this.serialNumber().trim();
    if (!sn) {
      this.error.set('Serial number is required');
      return;
    }

    this.loading.set(true);
    this.error.set('');
    this.result = null;
    this.formattedMessage.set('');
    this.crlUrl = null;

    this.certificateService.verifyCertificateStatus(sn).subscribe({
      next: (res) => {
        this.result = res;
        // Format response into a sentence emphasizing revoked status
        try {
          const revoked = !!res.revoked;
          const serial = res.serialNumber ?? sn;
          const reason = res.reason ?? null;
          const crl = res.crlUrl ?? null;
          this.crlUrl = crl;
          if (revoked) {
            const reasonText = reason ? ` (reason: ${reason})` : '';
            this.formattedMessage.set(`Certificate ${serial} is REVOKED${reasonText}.`);
          } else {
            this.formattedMessage.set(`Certificate ${serial} is NOT revoked.`);
          }
        } catch (e) {
          this.formattedMessage.set(JSON.stringify(res));
        }
      },
      error: (err) => {
        console.error('Verify error', err);
        this.error.set('Failed to verify certificate status');
      },
      complete: () => this.loading.set(false)
    });
  }
}
