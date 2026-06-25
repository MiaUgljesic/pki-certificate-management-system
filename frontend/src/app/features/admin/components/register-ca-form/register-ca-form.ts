import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService, Organization } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../shared/services/toast.service';

@Component({
  selector: 'app-register-ca-form',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './register-ca-form.html',
  styleUrls: ['./register-ca-form.css']
})
export class RegisterCAFormComponent implements OnInit {
  organizations = signal<Organization[]>([]);
  organizationId = signal<string | null>(null);
  newOrganizationName = signal<string>('');
  isNewOrganization = signal<boolean>(false);
  userEmail = signal<string>('');
  firstName = signal<string>('');
  lastName = signal<string>('');
  loading = signal<boolean>(false);
  errorMessage = signal<string>('');
  fieldErrors = signal<Record<string, string>>({});

  constructor(
    private readonly authService: AuthService,
    private readonly toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.fetchOrganizations();
  }

  private fetchOrganizations(): void {
    this.authService.getOrganizations().subscribe({
      next: (orgs: Organization[]) => this.organizations.set(orgs || []),
      error: () => {
        this.organizations.set([]);
        this.isNewOrganization.set(true);
        this.toastService.error('Failed to load organizations');
      }
    });
  }

  onOrganizationChange(event: Event): void {
    const value = (event.target as HTMLSelectElement)?.value ?? '';
    if (value === 'NEW') {
      this.isNewOrganization.set(true);
      this.organizationId.set(null);
    } else {
      this.isNewOrganization.set(false);
      this.newOrganizationName.set('');
      this.organizationId.set(value || null);
    }
    this.fieldErrors.update(errors => {
      const updated = { ...errors };
      delete updated['organizationId'];
      delete updated['newOrganizationName'];
      return updated;
    });
  }

  onNewOrganizationNameChange(value: string): void {
    this.newOrganizationName.set(value);
    this.fieldErrors.update(errors => {
      const updated = { ...errors };
      delete updated['newOrganizationName'];
      return updated;
    });
  }

  onUserEmailChange(value: string): void {
    this.userEmail.set(value);
    this.fieldErrors.update(errors => {
      const updated = { ...errors };
      delete updated['userEmail'];
      return updated;
    });
  }

  onFirstNameChange(value: string): void {
    this.firstName.set(value);
    this.fieldErrors.update(errors => {
      const updated = { ...errors };
      delete updated['firstName'];
      return updated;
    });
  }

  onLastNameChange(value: string): void {
    this.lastName.set(value);
    this.fieldErrors.update(errors => {
      const updated = { ...errors };
      delete updated['lastName'];
      return updated;
    });
  }

  private validateForm(): boolean {
    const errors: Record<string, string> = {};
    // Ensure we always provide organizationName (either new or from selection)
    let resolvedOrgName = '';
    if (this.isNewOrganization()) {
      resolvedOrgName = this.newOrganizationName().trim();
    } else {
      const id = this.organizationId();
      if (id) {
        const found = this.organizations().find(o => String(o.id) === String(id));
        resolvedOrgName = found ? (found.name || '') : '';
      }
    }

    if (!resolvedOrgName) {
      errors['organizationName'] = 'Organization name is required';
    }

    const email = this.userEmail().trim();
    if (!email) {
      errors['userEmail'] = 'Email is required';
    } else {
      // basic client-side email format check
      const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRe.test(email)) {
        errors['userEmail'] = 'Email should be valid';
      }
    }
    if (!this.firstName().trim()) {
      errors['firstName'] = 'First name is required';
    }
    if (!this.lastName().trim()) {
      errors['lastName'] = 'Last name is required';
    }

    this.fieldErrors.set(errors);
    return Object.keys(errors).length === 0;
  }

  register(): void {
    this.errorMessage.set('');

    if (!this.validateForm()) {
      return;
    }

    this.loading.set(true);

    const payload: any = {
      userEmail: this.userEmail().trim(),
      firstName: this.firstName().trim(),
      lastName: this.lastName().trim()
    };

    // always provide organizationName (backend expects this field)
    if (this.isNewOrganization()) {
      payload.organizationName = this.newOrganizationName().trim();
    } else {
      const id = this.organizationId();
      const found = id ? this.organizations().find(o => String(o.id) === String(id)) : null;
      payload.organizationName = found ? (found.name || '') : '';
    }

    this.authService.registerCA(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.toastService.success('Registration successful! Activation email sent.');
        this.organizationId.set(null);
        this.newOrganizationName.set('');
        this.userEmail.set('');
        this.firstName.set('');
        this.lastName.set('');
        this.fieldErrors.set({});
        this.isNewOrganization.set(false);
      },
      error: (error: any) => {
        this.loading.set(false);
        const msg = error?.error?.message || 'Registration failed. Please try again.';
        this.errorMessage.set(msg);
        this.toastService.error(msg);
      }
    });
  }

  onRegisterClick(event: Event): void {
    // ensure button click triggers registration even if form submit doesn't fire
    event.preventDefault();
    console.log('Register button clicked (onRegisterClick)');
    console.log('Current payload preview:', {
      organizationId: this.organizationId(),
      newOrganizationName: this.newOrganizationName(),
      userEmail: this.userEmail(),
      firstName: this.firstName(),
      lastName: this.lastName()
    });
    this.register();
  }
}