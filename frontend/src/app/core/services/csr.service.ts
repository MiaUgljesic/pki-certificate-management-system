import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';

export interface CaDTO {
  serialNumber: string;
  commonName: string;
  validTo: string; 
}
 
export interface OrganizationDTO {
  id: number;
  name: string;
}

@Injectable({
  providedIn: 'root'
})

export class CsrService {
  private readonly apiUrl = `${environment.apiUrl}/csrs`;

  constructor(private readonly http: HttpClient) { }

  getCAs(): Observable<CaDTO[]> {
    return this.http.get<CaDTO[]>(`${this.apiUrl}/issuers`);
  }
 
  getOrganizations(): Observable<OrganizationDTO[]> {
    return this.http.get<OrganizationDTO[]>(`${this.apiUrl}/organizations`);
  }

  uploadCsr(csrFile: File, issuerSerialNumber: string, validTo: number): Observable<string> {
    const formData = new FormData();
    formData.append('csrFile', csrFile);
    formData.append('issuerSerialNumber', issuerSerialNumber);
    formData.append('validTo', validTo.toString());
    return this.http.post(`${this.apiUrl}/upload`, formData, { responseType: 'text' });
  }
 
 autogenerate(payload: { commonName: string; organization: string; organizationalUnit?: string; country?: string; email?: string; 
        issuerSerialNumber: string; keyStorePassword: string; alias?: string; validTo: Date; includeSubjectKeyIdentifier: boolean;
      includeAuthorityKeyIdentifier: boolean; includeExtendedKeyUsage: boolean}): Observable<Blob> {
    return this.http.post(`${this.apiUrl}/autogenerate`, payload, { responseType: 'blob' });
  }
}