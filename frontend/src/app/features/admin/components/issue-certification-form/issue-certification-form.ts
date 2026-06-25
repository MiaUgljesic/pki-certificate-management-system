import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { CertificateService } from '../../../../core/services/certificate.service';
import { AuthService, Organization } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../shared/services/toast.service';
import { Button } from '../../../../shared/components/button/button';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-issue-certification-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, Button],
  templateUrl: './issue-certification-form.html',
  styleUrls: ['./issue-certification-form.css']
})
export class IssueCertificationFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  isSubmitting = false;
  isAdmin = false;
  isCaUser = false;
  organizations: Organization[] = [];
  loadingOrganizations = false;
  signingAuthorities: any[] = [];
  loadingSigningAuthorities = false;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private toastService: ToastService,
    private authService: AuthService
    ,
    private cdr: ChangeDetectorRef
  ) {
    this.initializeForm();
  }

  private loadOrganizations(): void {
    this.loadingOrganizations = true;
    this.authService.getOrganizations()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (orgs: Organization[]) => {
          this.organizations = orgs || [];
          this.loadingOrganizations = false;
          // ensure template updates immediately
          try { this.cdr.detectChanges(); } catch { /* noop */ }
        },
        error: () => {
          this.organizations = [];
          this.loadingOrganizations = false;
          this.toastService.error('Failed to load organizations');
          try { this.cdr.detectChanges(); } catch { /* noop */ }
        }
      });
  }

  ngOnInit(): void {
    this.isAdmin = this.authService.isAdmin();
    this.isCaUser = this.authService.getUserRole() === 'CA_USER';
    if (this.isAdmin) {
      this.loadOrganizations();
    }
    // load signing authorities for issuer selection
    this.loadSigningAuthorities();
    if (this.isCaUser) {
      const organizationName = this.authService.getOrganizationName();
      if (organizationName) {
        this.form.patchValue({ organization: organizationName });
      }
    }
    if (!this.isAdmin && this.form.get('certificateType')?.value === 'ROOT') {
      this.form.get('certificateType')?.setValue('END_ENTITY');
    }
    this.setupFormListeners();
    this.updateIssuerControl(this.form.get('certificateType')?.value);
    this.updateExtensionControls(this.form.get('certificateType')?.value);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeForm(): void {
    this.form = this.fb.group({
      commonName: ['', [Validators.required, Validators.minLength(1)]],
      organization: ['', Validators.required],
      organizationalUnit: [''],
      locality: [''],
      country: [''],
      email: [''],
      san: ['localhost'],
      certificateType: ['END_ENTITY', Validators.required],
      issuerSerialNumber: [''],
      validTo: ['', [Validators.required, this.futureDateValidator.bind(this)]],
      // Extension checkboxes
      includeSubjectKeyIdentifier: [false],
      includeAuthorityKeyIdentifier: [false],
      includeExtendedKeyUsage: [false]
    });
  }

  private loadSigningAuthorities(): void {
    this.loadingSigningAuthorities = true;
    this.certificateService.getSigningAuthorities()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list: any[]) => {
          this.signingAuthorities = list || [];
          this.loadingSigningAuthorities = false;
          try { this.cdr.detectChanges(); } catch {}
        },
        error: () => {
          this.signingAuthorities = [];
          this.loadingSigningAuthorities = false;
          this.toastService.error('Failed to load signing authorities');
        }
      });
  }

  private setupFormListeners(): void {
    this.form.get('certificateType')?.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        this.updateIssuerControl(value);
        this.updateExtensionControls(value);
      });

  }

  private futureDateValidator(control: AbstractControl): { [key: string]: any } | null {
    if (!control.value) {
      return null;
    }

    const selectedDate = new Date(control.value);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (selectedDate < today) {
      return { pastDate: true };
    }

    return null;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.toastService.error('Please fill in all required fields correctly');
      return;
    }

    this.isSubmitting = true;

    // Osiguraj da SAN ima vrijednost
    const sanValue = this.form.get('san')?.value || 'localhost';
    this.form.patchValue({ san: sanValue });

    const rawForm = this.form.getRawValue();

    const payload = {
      commonName: rawForm.commonName,
      organization: rawForm.organization,
      organizationalUnit: rawForm.organizationalUnit || undefined,
      locality: rawForm.locality || undefined,
      country: rawForm.country || undefined,
      email: rawForm.email || undefined,
      SAN: rawForm.san,
      type: rawForm.certificateType,
      issuerSerialNumber: rawForm.issuerSerialNumber || undefined,
      validTo: new Date(rawForm.validTo).toISOString().split('T')[0],
      includeSubjectKeyIdentifier: rawForm.includeSubjectKeyIdentifier || false,
      includeAuthorityKeyIdentifier: rawForm.certificateType === 'ROOT'
        ? false
        : (rawForm.includeAuthorityKeyIdentifier || false),
      includeExtendedKeyUsage: rawForm.certificateType === 'END_ENTITY'
        ? (rawForm.includeExtendedKeyUsage || false)
        : false
    };

    this.certificateService.issueCertificate(payload)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.finishSubmission())
      )
      .subscribe({
        next: () => {
          this.toastService.success('Certificate successfully issued!');
          this.form.reset({ certificateType: 'END_ENTITY', san: 'localhost' });
        },
        error: (error: any) => {
          const serverMessage = typeof error.error === 'string'
            ? error.error
            : (error.error?.message || error.error?.data);
          const errorMessage = serverMessage || 'An error occurred while issuing the certificate';
          this.toastService.error(errorMessage);
        }
      });
  }

  private finishSubmission(): void {
    setTimeout(() => {
      this.isSubmitting = false;
    });
  }

  resetForm(): void {
    this.form.reset({ certificateType: 'END_ENTITY', san: 'localhost' });
  }

  private updateIssuerControl(type: string | null | undefined): void {
    const issuerControl = this.form.get('issuerSerialNumber');
    if (type === 'ROOT') {
      issuerControl?.clearValidators();
      issuerControl?.disable();
      issuerControl?.reset();
    } else {
      issuerControl?.setValidators([Validators.required]);
      issuerControl?.enable();
    }
    issuerControl?.updateValueAndValidity();
  }

  private updateExtensionControls(type: string | null | undefined): void {
    const authorityKeyIdentifierControl = this.form.get('includeAuthorityKeyIdentifier');
    const extendedKeyUsageControl = this.form.get('includeExtendedKeyUsage');

    if (type === 'ROOT') {
      authorityKeyIdentifierControl?.setValue(false);
      authorityKeyIdentifierControl?.disable();
      extendedKeyUsageControl?.setValue(false);
      extendedKeyUsageControl?.disable();
      return;
    }

    authorityKeyIdentifierControl?.enable();

    if (type === 'END_ENTITY') {
      extendedKeyUsageControl?.enable();
    } else {
      extendedKeyUsageControl?.setValue(false);
      extendedKeyUsageControl?.disable();
    }
  }

  get commonNameError(): string | null {
    const control = this.form.get('commonName');
    if (control?.hasError('required')) {
      return 'Common Name is required';
    }
    return null;
  }

  get organizationError(): string | null {
    const control = this.form.get('organization');
    if (control?.hasError('required')) {
      return 'Organization is required';
    }
    return null;
  }

  get certificateTypeError(): string | null {
    const control = this.form.get('certificateType');
    if (control?.hasError('required')) {
      return 'Certificate Type is required';
    }
    return null;
  }

  get issuerSerialNumberError(): string | null {
    const control = this.form.get('issuerSerialNumber');
    if (control?.hasError('required') && this.form.get('certificateType')?.value !== 'ROOT') {
      return 'Issuer Serial Number is required';
    }
    return null;
  }

  get validToError(): string | null {
    const control = this.form.get('validTo');
    if (control?.hasError('required')) {
      return 'Valid To date is required';
    }
    if (control?.hasError('pastDate')) {
      return 'Valid To date cannot be in the past';
    }
    return null;
  }
}
