import { Component, signal, effect } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Button } from '../../../shared/components/button/button';
import { InputComponent } from '../../../shared/components/input-component/input-component';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../shared/services/toast.service';
import { PasswordValidator } from '../../../core/utils/password.validator';

@Component({
  selector: 'app-activate',
  standalone: true,
  imports: [CommonModule, Button, InputComponent],
  templateUrl: './activate.html',
  styleUrl: './activate.css'
})
export class ActivateComponent {
  password = signal('');
  confirmPassword = signal('');
  showPassword = signal(false);
  showConfirmPassword = signal(false);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  fieldErrors = signal<{ [key: string]: string }>({});
  token: string | null = null;

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token');
    if (!this.token) {
      this.errorMessage.set('Invalid activation link. Missing token.');
      this.toastService.error('Invalid activation link. Missing token.');
    }
  }

  onPasswordChange(value: string): void {
    this.password.set(value);
    this.validatePasswordRequirements();
    this.checkPasswordsMatch();
  }

  onConfirmPasswordChange(value: string): void {
    this.confirmPassword.set(value);
    this.checkPasswordsMatch();
  }

  togglePasswordVisibility(): void {
    this.showPassword.update(v => !v);
  }

  toggleConfirmPasswordVisibility(): void {
    this.showConfirmPassword.update(v => !v);
  }

  private validatePasswordRequirements(): void {
    const pwd = this.password();
    this.clearFieldError('password');

    if (!pwd) {
      this.fieldErrors.update(errors => ({
        ...errors,
        password: 'Password is required'
      }));
      return;
    }

    const validation = PasswordValidator.validateStrength(pwd);
    if (!validation.isValid) {
      this.fieldErrors.update(errors => ({
        ...errors,
        password: validation.errors.join(', ')
      }));
    }
  }

  private checkPasswordsMatch(): void {
    const pwd = this.password();
    const confirmPwd = this.confirmPassword();

    if (confirmPwd && pwd !== confirmPwd) {
      this.fieldErrors.update(errors => ({
        ...errors,
        confirmPassword: 'Passwords do not match'
      }));
    } else {
      this.clearFieldError('confirmPassword');
    }
  }

  private clearFieldError(field: string): void {
    this.fieldErrors.update(errors => {
      const updated = { ...errors };
      delete updated[field];
      return updated;
    });
  }

  private validateForm(): boolean {
    const errors: { [key: string]: string } = {};
    const pwd = this.password();
    const confirmPwd = this.confirmPassword();

    if (!pwd) {
      errors['password'] = 'Password is required';
    } else {
      const validation = PasswordValidator.validateStrength(pwd);
      if (!validation.isValid) {
        errors['password'] = validation.errors.join(', ');
      }
    }

    if (!confirmPwd) {
      errors['confirmPassword'] = 'Please confirm your password';
    } else if (!PasswordValidator.passwordsMatch(pwd, confirmPwd)) {
      errors['confirmPassword'] = 'Passwords do not match';
    }

    this.fieldErrors.set(errors);
    return Object.keys(errors).length === 0;
  }

  activate(): void {
    this.errorMessage.set('');
    this.successMessage.set('');

    if (!this.token) {
      this.errorMessage.set('Invalid activation link. Missing token.');
      return;
    }

    if (!this.validateForm()) {
      return;
    }

    this.loading.set(true);

    const payload = {
      token: this.token,
      password: this.password()
    };

    this.authService.activate(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.successMessage.set('Account activated successfully! Redirecting to login...');
        this.toastService.success('Account activated successfully!');
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2000);
      },
      error: (error: any) => {
        this.loading.set(false);
        const errorMsg = error?.error?.message || 'Activation failed. Please try again or request a new activation link.';
        this.errorMessage.set(errorMsg);
        this.toastService.error(errorMsg);
      }
    });
  }

  isRequirementMet(requirement: string): boolean {
    return false;
  }

  getPasswordErrorMessages(): string[] {
    const errorMsg = this.fieldErrors()['password'];
    if (!errorMsg) return [];
    // Return only the first error
    const errors = errorMsg.split(', ');
    return errors.length > 0 ? [errors[0]] : [];
  }
}
