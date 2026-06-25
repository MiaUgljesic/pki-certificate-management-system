import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { User } from '../../shared/models/certificate-overview/User';


@Injectable({
  providedIn: 'root'
})

export class ProfileService {
  private readonly apiUrl = `${environment.apiUrl}/profiles`;

  constructor(private readonly http: HttpClient) { }

  getProfile() : Observable<User> {
    return this.http.get<User>(`${this.apiUrl}`);
  }

  generateQrCode() {
    return this.http.post(`${this.apiUrl}/2fa/generate`, {});
  }

  verifyTwoFactor(code: string) {
    return this.http.post(`${this.apiUrl}/2fa/verify`, { code });
  }

  disableTwoFactor() {
    return this.http.post(`${this.apiUrl}/2fa/disable`, {});
  }  
}
