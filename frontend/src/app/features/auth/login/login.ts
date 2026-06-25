import { Component, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ForgotPasswordComponent } from '../forgot-password/forgot-password';
import { AuthService } from '../../../core/services/auth.service';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule, ForgotPasswordComponent],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginComponent {
 email = signal('');
  password = signal('');
  loading = signal(false);
  errorMessage = signal('');
  twoFactorRequired = false;
  twoFactorCode = '';

  showForgotPassword = signal(false);
  showPassword = signal(false);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) { }

  onEmailChange(value: string): void {
    this.email.set(value);
  }

  onPasswordChange(value: string): void {
    this.password.set(value);
  }

  login(): void {
    this.errorMessage.set('');

    const email = this.email().trim();
    const password = this.password();

    if (!email || !password) {
      this.errorMessage.set('Email and password are required.');
      return;
    }

    this.loading.set(true);
    this.authService
      .login({ email, password })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          if (response.twoFactorRequired) {
            this.twoFactorRequired = true;
            return;
          }
          this.navigateByRole(response.role ?? this.authService.getUserRole());
        },
        error: () => {
          this.errorMessage.set('Invalid credentials. Please try again.');
        }
      });
  }

  verify2FA(): void {
    if (!this.twoFactorCode || this.twoFactorCode.length !== 6) {
      this.errorMessage.set('Please enter a valid 6-digit code.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    this.authService.loginWith2FA(this.email(), this.password(), this.twoFactorCode)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          if(response.accessToken){
            this.authService.setAccessToken(response.accessToken);
          }
          if(response.refreshToken){
            this.authService.setRefreshToken(response.refreshToken);
          }
          if (response.role) {
            this.authService.setUserRole(response.role);
          }
          if (response.role === 'CA_USER' && response.organizationName) {
            this.authService.setOrganizationName(response.organizationName);
          }
          this.navigateByRole(response.role ?? this.authService.getUserRole());
        },
        error: () => {
          this.errorMessage.set('Invalid code. Please try again.');
        }
      });
  }

  private navigateByRole(role: string): void {
    switch (role) {
      case 'ADMIN':
        this.router.navigate(['/admin/all-certificates']);
        break;
      case 'CA_USER':
        this.router.navigate(['/ca/organization-certificates']);
        break;
      default:
        this.router.navigate(['/user-certificate-overview']);
    }
  }

  toggleForgotPassword(): void {
    this.showForgotPassword.set(!this.showForgotPassword());
  }
}