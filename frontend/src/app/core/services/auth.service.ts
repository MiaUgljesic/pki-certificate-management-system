import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

interface LoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  role: string;
  organizationName?: string | null;
  twoFactorRequired: boolean
}

interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

interface RegistrationRequest {
  organizationName: string;
  userEmail: string;
  firstName: string;
  lastName: string;
}

interface ActivationRequest {
  token: string;
  password: string;
}

export interface Organization {
  id: number;
  name: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly accessTokenKey = 'accessToken';
  private readonly refreshTokenKey = 'refreshToken';
  private readonly userRoleKey = 'userRole';
  private readonly organizationNameKey = 'organizationName';
  private readonly baseUrl = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  login(payload: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.baseUrl}/auth/login`, payload)
      .pipe(
        tap((response) => {
          const accessToken = response.accessToken ?? (response as any).access_token;
          const refreshToken = response.refreshToken ?? (response as any).refresh_token;

          if (accessToken) {
            this.setAccessToken(accessToken);
          }

          if (refreshToken) {
            this.setRefreshToken(refreshToken);
          }
          this.setUserRole(response.role);

          if (response.role === 'CA_USER' && response.organizationName) {
            this.setOrganizationName(response.organizationName);
          } else {
            this.clearOrganizationName();
          }
        })
      );
  }

  loginWith2FA(email: string, password: string, code: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/auth/login/2fa`, { email, password, code });
  }

  setAccessToken(token: string): void {
    localStorage.setItem(this.accessTokenKey, token);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.accessTokenKey);
  }

  setRefreshToken(token: string): void {
    localStorage.setItem(this.refreshTokenKey, token);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }

  setUserRole(role: string): void {
    localStorage.setItem(this.userRoleKey, role);
  }

  getUserRole(): string | null {
    return localStorage.getItem(this.userRoleKey);
  }

  setOrganizationName(name: string): void {
    localStorage.setItem(this.organizationNameKey, name);
  }

  getOrganizationName(): string | null {
    return localStorage.getItem(this.organizationNameKey);
  }

  clearOrganizationName(): void {
    localStorage.removeItem(this.organizationNameKey);
  }

  isLoggedIn(): boolean {
    return !!this.getAccessToken();
  }

  isAdmin(): boolean {
    return this.getUserRole() === 'ADMIN';
  }

  logout(): void {
    localStorage.removeItem(this.accessTokenKey);
    localStorage.removeItem(this.refreshTokenKey);
    localStorage.removeItem(this.userRoleKey);
    localStorage.removeItem(this.organizationNameKey);
  }

  refresh(): Observable<RefreshResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      console.warn('[auth] refresh() called without refresh token');
      throw new Error('Missing refresh token');
    }
    return this.http.post<RefreshResponse>(`${this.baseUrl}/auth/refresh`, { refreshToken });
  }

  registerCA(payload: RegistrationRequest): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/register-ca`, payload);
  }

  activate(payload: ActivationRequest): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/activate`, payload);
  }

  getOrganizations(): Observable<Organization[]> {
    console.log('Fetching organizations from:', `${this.baseUrl}/organizations/all`);
    return this.http.get<any>(`${this.baseUrl}/organizations/all`);
  }

  initiatePasswordReset(email: string): Observable<string> {
    console.log('Initiating password reset for email:', email);
    return this.http.post<string>(
      `${this.baseUrl}/auth/forgot-password`,
      { email },
      { responseType: 'text' as 'json' }
    ).pipe(
      tap((response) => {
        console.log('Password reset initiated. Response:', response);
      })
    );
  }

  completePasswordReset(payload: any): Observable<string> {
    console.log('Completing password reset with payload:', payload);
    return this.http.post<string>(
      `${this.baseUrl}/auth/reset-password`,
      payload,
      { responseType: 'text' as 'json' }
    ).pipe(
      tap((response) => {
        console.log('Password reset completed. Response:', response);
      })
    );
  }
}
