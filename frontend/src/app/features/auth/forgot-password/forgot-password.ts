import { Component, Output, EventEmitter, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './forgot-password.html',
  styleUrls: ['./forgot-password.css']
})
export class ForgotPasswordComponent implements OnDestroy {
  @Output() backToLogin = new EventEmitter<void>();

  email: string = '';
  loading: boolean = false;
  successMessage: string = '';
  errorMessage: string = '';

  private destroy$ = new Subject<void>();

  constructor(
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  isEmailValid(): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(this.email.trim());
  }

  submitForgotPassword(): void {
    this.errorMessage = '';
    this.successMessage = '';

    const emailTrimmed = this.email.trim();
    if (!emailTrimmed) {
      this.errorMessage = 'Please enter your email address.';
      return;
    }

    if (!this.isEmailValid()) {
      this.errorMessage = 'Please enter a valid email address.';
      return;
    }

    this.loading = true;

    this.authService.initiatePasswordReset(emailTrimmed)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.successMessage = '✓ Recovery link sent! Check your email for the secure cryptographic challenge.';
          this.email = '';
          this.cdr.markForCheck();
          
          setTimeout(() => {
            this.backToLogin.emit();
          }, 2500);
        },
        error: (err) => {
          this.errorMessage = `Recovery failed: ${err.error?.message || err.message || 'Unknown error'}`;
          this.cdr.markForCheck();
        },
        complete: () => {
          this.loading = false;
          this.cdr.markForCheck();
        }
      });
  }

  handleBackClick(): void {
    this.backToLogin.emit();
  }
}
