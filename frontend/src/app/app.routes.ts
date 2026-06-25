import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login';
import { RegisterComponent } from './features/auth/register/register';
import { ActivateChallengeComponent } from './features/auth/activate-challenge/activate-challenge';
import { RecoverAccountComponent } from './features/auth/recover-account/recover-account';
import { IssueCertificationFormComponent } from './features/admin/components/issue-certification-form/issue-certification-form';
import { RegisterCAComponent } from './features/admin/pages/register-ca/register-ca';
import { ActivateComponent } from './features/auth/activate/activate';
import { roleGuard } from './core/guards/role.guard';
import { UserOverview } from './features/certificate-overview/pages/user-overview/user-overview';
import { CertificateDetails } from './features/certificate-overview/pages/certificate-details/certificate-details';
import { AllCertificates } from './features/admin/pages/all-certificates/all-certificates';
import { OrganizationCertificates } from './features/ca-user/pages/organization-certificates/organization-certificates';
import { UserProfile } from './features/profile/pages/user-profile/user-profile';
import { Csr } from './features/csr/pages/csr/csr';
import { VerifySerialComponent } from './features/revocation-check/pages/verify-serial/verify-serial';

export const routes: Routes = [
	{ path: '', redirectTo: 'login', pathMatch: 'full' },
	{ path: 'login', component: LoginComponent },
	{ path: 'register', component: RegisterComponent },
	{ path: 'activate-challenge/:token', component: ActivateChallengeComponent },
	{ path: 'recover-account/:token', component: RecoverAccountComponent },

	// ADMIN
	{
		path: 'issue-certificate',
		component: IssueCertificationFormComponent,
		canActivate: [roleGuard],
		data: { requiredRoles: ['ADMIN', 'CA_USER'] }
	},
	{
		path: 'admin/register-ca',
		component: RegisterCAComponent,
		canActivate: [roleGuard],
		data: { requiredRole: 'ADMIN' }
	},
	{
		path: 'admin/all-certificates',
		component: AllCertificates,
		canActivate: [roleGuard],
		data: { requiredRole: 'ADMIN' }
	},
	{
		path: 'ca/organization-certificates',
		component: OrganizationCertificates,
		canActivate: [roleGuard],
		data: { requiredRole: 'CA_USER' }
	},
	{
		path: 'activate/:token',
		component: ActivateComponent
	},
	{
		path: 'user-certificate-overview',
		component: UserOverview,
		canActivate: [roleGuard],
		data: { requiredRole: 'USER' }
	},
	{ path: 'overview-certificate-details/:id', component: CertificateDetails },
	{
		path: 'user-profile',
		component: UserProfile,
		canActivate: [roleGuard],
		data: { requiredRole: 'USER' }
	},
	{
		path: 'csr',
		component: Csr,
		canActivate: [roleGuard],
		data: { requiredRole: 'USER' }
	}
	,
	{ path: 'revocation-check', component: VerifySerialComponent }
];