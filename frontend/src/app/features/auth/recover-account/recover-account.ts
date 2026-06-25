import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-recover-account',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recover-account.html',
  styleUrls: ['./recover-account.css']
})
export class RecoverAccountComponent implements OnInit, OnDestroy {
  token: string = '';
  challenge: string = '';
  selectedFileName: string = '';
  privateKeyPem: string = '';
  newPassword: string = '';
  confirmPassword: string = '';
  showPassword: boolean = false;
  showConfirmPassword: boolean = false;
  loading: boolean = false;
  isSuccess: boolean = false;
  errorMessage: string = '';

  private destroy$ = new Subject<void>();

  private passwordValidationPatterns = {
    minLength: /.{8,}/,
    uppercase: /[A-Z]/,
    lowercase: /[a-z]/,
    number: /\d/,
    special: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.token = params['token'];
    });
    this.route.queryParams.subscribe(params => {
      this.challenge = params['challenge'] || '';
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      this.selectedFileName = file.name;
      const reader = new FileReader();
      reader.onload = (e) => {
        this.privateKeyPem = e.target?.result as string;
      };
      reader.readAsText(file);
    }
  }

  clearFileSelection(): void {
    this.selectedFileName = '';
    this.privateKeyPem = '';
    const fileInput = document.getElementById('privateKeyFile') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  toggleConfirmPasswordVisibility(): void {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  hasPasswordMismatch(): boolean {
    return this.newPassword !== this.confirmPassword && this.confirmPassword.length > 0;
  }

  getPasswordValidationError(): string {
    if (!this.passwordValidationPatterns.minLength.test(this.newPassword)) {
      return 'PASSWORD MUST BE AT LEAST 8 CHARACTERS LONG';
    }
    if (!this.passwordValidationPatterns.uppercase.test(this.newPassword)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE UPPERCASE LETTER';
    }
    if (!this.passwordValidationPatterns.lowercase.test(this.newPassword)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE LOWERCASE LETTER';
    }
    if (!this.passwordValidationPatterns.number.test(this.newPassword)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE NUMBER';
    }
    if (!this.passwordValidationPatterns.special.test(this.newPassword)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE SPECIAL CHARACTER';
    }
    return '';
  }

  hasPasswordValidationError(): boolean {
    return this.newPassword.length > 0 && this.getPasswordValidationError() !== '';
  }

  isFormValid(): boolean {
    return (
        !!this.token &&
        !!this.challenge &&
        !!this.privateKeyPem &&
        this.newPassword.length >= 8 &&
        this.confirmPassword.length >= 8 &&
        this.newPassword === this.confirmPassword
    );
  }

  async resetPassword(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';

    try {
      if (!this.token || !this.challenge || !this.privateKeyPem) {
        throw new Error('Missing token, challenge, or private key');
      }

      let sanitizedChallenge = this.challenge.replace(/ /g, '+');
      const privateKey = await this.importPrivateKey(this.privateKeyPem);
      const encryptedBuffer = this.base64ToArrayBuffer(sanitizedChallenge);
      const decryptedBuffer = await window.crypto.subtle.decrypt(
        {
          name: 'RSA-OAEP'
        },
        privateKey,
        encryptedBuffer
      );

      const decryptedChallenge = new TextDecoder().decode(decryptedBuffer);
      const payload = {
        token: this.token,
        decryptedChallenge: decryptedChallenge,
        newPassword: this.newPassword
      };

      return new Promise<void>((resolve, reject) => {
        this.authService.completePasswordReset(payload)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.isSuccess = true;
              this.cdr.markForCheck();
              resolve();
            },
            error: (err) => {
              reject(err);
            }
          });
      });
    } catch (error) {
      this.errorMessage = `Password reset failed: ${error instanceof Error ? error.message : String(error)}`;
    } finally {
      this.loading = false;
      this.cdr.markForCheck();
    }
  }

  navigateToLogin(): void {
    this.router.navigate(['/login']);
  }

  private async importPrivateKey(pemString: string): Promise<CryptoKey> {
    try {
      const pemHeader = '-----BEGIN PRIVATE KEY-----';
      const pemFooter = '-----END PRIVATE KEY-----';
      const headerIndex = pemString.indexOf(pemHeader);
      const footerIndex = pemString.indexOf(pemFooter);

      if (headerIndex === -1 || footerIndex === -1) {
        throw new Error('Invalid PEM format: Missing BEGIN or END markers');
      }

      let pemContents = pemString.substring(
        headerIndex + pemHeader.length,
        footerIndex
      );

      pemContents = pemContents
        .trim()
        .replace(/\r/g, '')
        .replace(/\n/g, '')
        .replace(/\s/g, '');

      const binaryString = atob(pemContents);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      const importedKey = await window.crypto.subtle.importKey(
        'pkcs8',
        bytes.buffer,
        {
          name: 'RSA-OAEP',
          hash: 'SHA-256'
        },
        false,
        ['decrypt']
      );
      return importedKey;
    } catch (error) {
      throw error;
    }
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    try {
      const binaryString = atob(base64);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      return bytes.buffer;
    } catch (error) {
      throw error;
    }
  }
}
