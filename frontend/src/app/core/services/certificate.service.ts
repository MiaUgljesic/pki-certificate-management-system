import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaginatedCertificateOverviewResponse } from '../../shared/models/certificate-overview/PaginatedCertificateOverviewResponse';
import { PaginatedCertificateResponseDTO } from '../../shared/models/certificate-overview/PaginatedCertificateResponseDTO';

export interface IssueCertificateRequest {
  commonName: string;
  organization: string;
  organizationalUnit?: string;
  locality?: string;
  country?: string;
  email?: string;
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  issuerSerialNumber?: string;
  validTo: string;
}

export interface IssueCertificateResponse {
  id: string;
  serialNumber: string;
  commonName: string;
  organization: string;
  certificateType: string;
  issuedAt: string;
  validTo: string;
  status: string;
}

export interface CertificateError {
  message: string;
  code?: string;
}

export interface DownloadCertificateRequest {
  serialNumber: string;
  keyStorePassword: string;
  alias?: string;
}

export interface CrlItemResponseDTO {
  serialNumber: string;
  reason: string;
  revokedAt: string;
}

export enum RevocationReason {
  UNSPECIFIED = 'UNSPECIFIED',
  KEY_COMPROMISE = 'KEY_COMPROMISE',
  CA_COMPROMISE = 'CA_COMPROMISE',
  AFFILIATION_CHANGED = 'AFFILIATION_CHANGED',
  SUPERSEDED = 'SUPERSEDED',
  CESSATION_OF_OPERATION = 'CESSATION_OF_OPERATION',
  CERTIFICATE_HOLD = 'CERTIFICATE_HOLD',
  REMOVE_FROM_CRL = 'REMOVE_FROM_CRL',
  PRIVILEGE_WITHDRAWN = 'PRIVILEGE_WITHDRAWN',
  AA_COMPROMISE = 'AA_COMPROMISE'
}

@Injectable({
  providedIn: 'root'
})
export class CertificateService {
  private readonly apiUrl = `${environment.apiUrl}/certificates`;

  constructor(private readonly http: HttpClient) { }

  issueCertificate(payload: IssueCertificateRequest): Observable<string> {
    return this.http.post(`${this.apiUrl}/issue`, payload, { responseType: 'text' });
  }

  getOrganizations(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/organizations`);
  }

  getUserCertificateOverview(page: number = 0, size: number = 8, sortBy?: string, sortDir?: string, date?: number): Observable<PaginatedCertificateOverviewResponse> {
    let url = `${this.apiUrl}/user-overview?page=${page}&size=${size}`;

    if (sortBy && sortDir) {
      url += `&sortBy=${sortBy}&sortDir=${sortDir}`;
    }
    if (date !== undefined && date !== null) {
      url += `&date=${date}`;
    }
    return this.http.get<PaginatedCertificateOverviewResponse>(url);
  }

  getAllCertificates(page: number = 0, size: number = 8, sortBy?: string, sortDir?: string, date?: number): Observable<PaginatedCertificateResponseDTO> {
    let url = `${this.apiUrl}/all?page=${page}&size=${size}`;

    if (sortBy && sortDir) {
      url += `&sort=${sortBy},${sortDir}`;
    }
    if (date !== undefined && date !== null) {
      url += `&date=${date}`;
    }
    return this.http.get<PaginatedCertificateResponseDTO>(url);
  }

  getOrganizationCertificates(page: number = 0, size: number = 8, sortBy?: string, sortDir?: string, date?: number): Observable<PaginatedCertificateResponseDTO> {
    let url = `${this.apiUrl}/organization?page=${page}&size=${size}`;

    if (sortBy && sortDir) {
      url += `&sort=${sortBy},${sortDir}`;
    }
    if (date !== undefined && date !== null) {
      url += `&date=${date}`;
    }
    return this.http.get<PaginatedCertificateResponseDTO>(url);
  }

  downloadCertificate(payload: DownloadCertificateRequest): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.apiUrl}/download`, payload, {
      observe: 'response',
      responseType: 'blob'
    });
  }

  downloadCertificateByFormat(data: { serialNumber: string; format: 'PEM' | 'CER' }): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.apiUrl}/download/${data.serialNumber}`, {
      params: { format: data.format },
      responseType: 'blob',
      observe: 'response',  
    });
  }

  revokeCertificate(serialNumber: string, reason: string): Observable<any> {
    const body = { serialNumber, reason };
    return this.http.post<any>(`${this.apiUrl}/revoke`, body);
  }

  unrevokeCertificate(serialNumber: string): Observable<string> {
    return this.http.post(`${this.apiUrl}/unrevoke/${serialNumber}`, null, { responseType: 'text' });
  }
  
  getSigningAuthorities(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/signing-authorities`);
  }

  getCrl(): Observable<CrlItemResponseDTO[]> {
    return this.http.get<CrlItemResponseDTO[]>(`${this.apiUrl}/crl`);
  }

  downloadCRL(caSerialNumber: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.apiUrl}/crl/${caSerialNumber}.crl`, {
      responseType: 'blob',
      observe: 'response'
    });
  }

  verifyCertificateStatus(serialNumber: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/verify-status/${serialNumber}`);
  }
}
