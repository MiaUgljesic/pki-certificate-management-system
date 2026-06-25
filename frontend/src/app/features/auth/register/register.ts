import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RegistrationService, AuthService } from '../../../core/services';
import { Organization } from '../../../core/services/auth.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

function passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.parent) {
    return null;
  }
  const password = control.parent.get('password')?.value;
  const confirmPassword = control.value;
  return password && confirmPassword && password !== confirmPassword
    ? { passwordMismatch: true }
    : null;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './register.html',
  styleUrls: ['./register.css']
})
export class RegisterComponent implements OnInit, OnDestroy {
  registerForm!: FormGroup;
  loading = false;
  keyGenerating = false;
  errorMessage = '';
  successMessage = '';
  showPassword = false;
  showConfirmPassword = false;
  publicKeyGenerated = false;
  privateKeyDownloaded = false;
  currentStep: 'user-info' | 'key-generation' = 'user-info';
  organizations: Organization[] = [];
  isSuccess = false;
  isError = false;
  errorDetails = '';

  private destroy$ = new Subject<void>();

  constructor(
    private formBuilder: FormBuilder,
    private registrationService: RegistrationService,
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  private passwordValidationPatterns = {
    minLength: /.{8,}/,
    uppercase: /[A-Z]/,
    lowercase: /[a-z]/,
    number: /\d/,
    special: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/
  };

  ngOnInit(): void {
    this.initializeForm();
    this.fetchOrganizations();
  }

  private fetchOrganizations(): void {
    this.authService
      .getOrganizations()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (orgs) => {
          
          if (Array.isArray(orgs)) {
            console.log('Number of organizations:', orgs.length);
            console.log('First organization:', orgs[0]);
          }
          this.organizations = orgs;
          this.cdr.markForCheck();
          console.log('Organizations assigned to component:', this.organizations);
        },
        error: (error) => {
          console.error('Failed to fetch organizations:', error);
          this.errorMessage = 'Failed to load organizations';
          this.cdr.markForCheck();
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeForm(): void {
    this.registerForm = this.formBuilder.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required, passwordMatchValidator]],
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required, Validators.minLength(2)]],
      organization: ['', Validators.required],
      publicKeyPem: ['', Validators.required]
    });
  }

  isStep1Valid(): boolean {
    const step1Fields = ['email', 'password', 'confirmPassword', 'firstName', 'lastName', 'organization'];
    return step1Fields.every(fieldName => {
      const field = this.registerForm.get(fieldName);
      return field && field.valid;
    });
  }

  proceedToStep2(): void {
    if (this.isStep1Valid()) {
      this.currentStep = 'key-generation';
      this.errorMessage = '';
      this.successMessage = '';
    } else {
      this.errorMessage = 'Please complete all fields in Step 1 correctly';
    }
  }

  backToStep1(): void {
    this.currentStep = 'user-info';
    this.errorMessage = '';
    this.successMessage = '';
  }

  async generateKeyPair(): Promise<void> {
    this.keyGenerating = true;
    this.errorMessage = '';
    this.successMessage = '';

    try {
      const keyPair = await window.crypto.subtle.generateKey(
        {
          name: 'RSA-OAEP',
          modulusLength: 2048,
          publicExponent: new Uint8Array([1, 0, 1]), // 65537
          hash: 'SHA-256'
        },
        true,
        ['encrypt', 'decrypt']
      );

      const publicKeyBuffer = await window.crypto.subtle.exportKey('spki', keyPair.publicKey);
      const publicKeyPem = this.arrayBufferToPem(publicKeyBuffer, 'PUBLIC KEY');

      const privateKeyBuffer = await window.crypto.subtle.exportKey('pkcs8', keyPair.privateKey);
      const privateKeyPem = this.arrayBufferToPem(privateKeyBuffer, 'PRIVATE KEY');

      this.registerForm.patchValue({ publicKeyPem });
      this.publicKeyGenerated = true;

      this.downloadPrivateKey(privateKeyPem);
      this.privateKeyDownloaded = true;

      this.successMessage = 'Private Key downloaded successfully. Keep it safe!';
    } catch (error) {
      this.errorMessage = `Key generation failed: ${error instanceof Error ? error.message : String(error)}`;
    } finally {
      this.keyGenerating = false;
    }
  }

  onSubmit(): void {
    if (!this.registerForm.valid) {
      this.errorMessage = 'Please fill in all required fields correctly';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.isSuccess = false;
    this.isError = false;

    const registrationData = this.registerForm.value;
    console.log('Sending registration request payload:', registrationData);

    this.registrationService
      .register(registrationData)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          console.log('Registration successful backend response:', response);
          this.isSuccess = true;
          this.isError = false;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('Registration failed backend error:', err);
          this.isSuccess = false;
          this.isError = true;
          this.errorDetails = err.error?.message || err.error || 'Oops, something went wrong!';
          this.loading = false;
          this.cdr.markForCheck();
        }
      });
  }

  tryAgain(): void {
    this.isError = false;
    this.isSuccess = false;
    this.errorDetails = '';
    this.currentStep = 'user-info';
    this.cdr.markForCheck();
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  toggleConfirmPasswordVisibility(): void {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  private arrayBufferToPem(buffer: ArrayBuffer, keyType: string): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    const base64 = btoa(binary);
    const lines: string[] = [];
    for (let i = 0; i < base64.length; i += 64) {
      lines.push(base64.substr(i, 64));
    }

    return `-----BEGIN ${keyType}-----\n${lines.join('\n')}\n-----END ${keyType}-----`;
  }

  private downloadPrivateKey(privateKeyPem: string): void {
    const element = document.createElement('a');
    const file = new Blob([privateKeyPem], { type: 'text/plain' });
    element.href = URL.createObjectURL(file);
    element.download = 'private_key.pem';
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
    URL.revokeObjectURL(element.href);
  }

  hasError(fieldName: string, errorType: string): boolean {
    const field = this.registerForm.get(fieldName);
    return !!(field && field.hasError(errorType) && field.touched);
  }

  getPasswordValidationError(): string {
    const password = this.registerForm.get('password')?.value || '';
    
    if (!this.passwordValidationPatterns.minLength.test(password)) {
      return 'PASSWORD MUST BE AT LEAST 8 CHARACTERS LONG';
    }
    if (!this.passwordValidationPatterns.uppercase.test(password)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE UPPERCASE LETTER';
    }
    if (!this.passwordValidationPatterns.lowercase.test(password)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE LOWERCASE LETTER';
    }
    if (!this.passwordValidationPatterns.number.test(password)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE NUMBER';
    }
    if (!this.passwordValidationPatterns.special.test(password)) {
      return 'PASSWORD MUST CONTAIN AT LEAST ONE SPECIAL CHARACTER';
    }
    return '';
  }

  hasPasswordValidationError(): boolean {
    return !!this.registerForm.get('password')?.touched && this.getPasswordValidationError() !== '';
  }

  hasPasswordMismatch(): boolean {
    const password = this.registerForm.get('password')?.value;
    const confirmPassword = this.registerForm.get('confirmPassword')?.value;
    return confirmPassword && password !== confirmPassword;
  }

  isTouched(fieldName: string): boolean {
    return !!(this.registerForm.get(fieldName)?.touched);
  }

}
