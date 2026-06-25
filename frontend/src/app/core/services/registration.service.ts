import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';


export interface RegistrationRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  organization: string;
  publicKeyPem: string;
}

/**
 * Interface for registration response
 */
export interface RegistrationResponse {
  success?: boolean;
  message?: string;
  userId?: string;
  [key: string]: any;
}

/**
 * Service for handling user registration with RSA key pair
 */
@Injectable({
  providedIn: 'root'
})
export class RegistrationService {
      private readonly apiUrl = `${environment.apiUrl}/auth/register`;

  constructor(private http: HttpClient) {}

  register(registrationData: any): Observable<string> {
  return this.http.post(this.apiUrl, registrationData, {
    responseType: 'text'
  });
}
}
