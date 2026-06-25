import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { CertificateService } from '../../../../core/services/certificate.service';
import { Certificate } from '../../../../shared/models/certificate-overview/Certificates';
import { CertificateResponseDTO } from '../../../../shared/models/certificate-overview/CertificateResponseDTO';
import { PaginatedCertificateResponseDTO } from '../../../../shared/models/certificate-overview/PaginatedCertificateResponseDTO';
import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { CommonModule } from '@angular/common';
import { AdminCertificatesTable } from '../../components/admin-certificates-table/admin-certificates-table';
import { Button } from '../../../../shared/components/button/button';
import { DownloadCertificateModal } from '../../../../shared/components/download-certificate-modal/download-certificate-modal';

const RevocationReasonLabels: { [key: string]: string } = {
  UNSPECIFIED: 'Unspecified',
  KEY_COMPROMISE: 'Key compromise',
  CA_COMPROMISE: 'CA compromise',
  AFFILIATION_CHANGED: 'Affiliation changed',
  SUPERSEDED: 'Superseded',
  CESSATION_OF_OPERATION: 'Cessation of operation',
  CERTIFICATE_HOLD: 'Certificate hold',
  REMOVE_FROM_CRL: 'Removed from CRL',
  PRIVILEGE_WITHDRAWN: 'Privilege withdrawn',
  AA_COMPROMISE: 'AA compromise'
};

@Component({
  selector: 'app-all-certificates',
  imports: [PageHeader, CommonModule, AdminCertificatesTable, Button, DownloadCertificateModal],
  templateUrl: './all-certificates.html',
  styleUrl: './all-certificates.css',
})
export class AllCertificates implements OnInit {
  allCertificates: Certificate[] = [];
  filteredCertificates: Certificate[] = [];

  currentPage: number = 0;
  pageSize: number = 8;
  totalPages: number = 0;
  totalElements: number = 0;
  isFirstPage: boolean = true;
  isLastPage: boolean = false;

  currentSortBy: string = 'validFrom';
  currentSortDir: string = 'desc';

  currentFilterDate?: number;

  selectedDownloadCertificate?: Certificate;

  constructor(private certificateService: CertificateService, private cdr: ChangeDetectorRef) {}
  @Input() initialSortColumn: string = '';
  @Input() initialSortDirection: 'asc' | 'desc' = 'asc';

  ngOnInit(): void {
    this.loadCertificates(this.currentPage, this.currentSortBy, this.currentSortDir, this.currentFilterDate);
  }

  private loadCertificates(page: number = 0, sortBy?: string, sortDir?: string, date?: number) {
    this.certificateService.getAllCertificates(page, this.pageSize, sortBy, sortDir, date).subscribe({
      next: (data: PaginatedCertificateResponseDTO) => {
        this.allCertificates = this.transformCertificateData(data.content);
        this.filteredCertificates = [...this.allCertificates];
        this.currentPage = data.number;
        this.totalPages = data.totalPages;
        this.totalElements = data.totalElements;
        this.isFirstPage = data.first;
        this.isLastPage = data.last;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Error loading certificates overview:', error);
      }
    });
  }

  transformCertificateData(apiData: CertificateResponseDTO[]): Certificate[] {
    return apiData.map((certificate) => {
      return {
        id: certificate.id,
        serialNumber: certificate.serialNumber,
        commonName: certificate.commonName,
        organizationName: certificate.organizationName ?? 'N/A',
        organizationalUnit: certificate.organizationalUnit ?? 'N/A',
        country: certificate.country ?? 'N/A',
        email: certificate.email ?? 'N/A',
        status: certificate.status,
        validFrom: new Date(certificate.validFrom).toLocaleDateString('sr-RS'),
        validTo: new Date(certificate.validTo).toLocaleDateString('sr-RS'),
        keyAlgorithm: certificate.keyAlgorithm ?? 'N/A',
        keySize: certificate.keySize != null ? Number(certificate.keySize) : null,
        signatureAlgorithm: certificate.signatureAlgorithm ?? 'N/A',
        revocationReason: certificate.revocationReason ? (RevocationReasonLabels[certificate.revocationReason] || certificate.revocationReason) : 'N/A',
        revokedAt: certificate.revokedAt ? new Date(certificate.revokedAt).toLocaleDateString('sr-RS') : 'N/A',
        issuerSerialNumber: certificate.issuerSerialNumber ?? null,
        certificateType: certificate.certificateType ?? null,
        SAN: certificate.SAN ?? null,
        includeSubjectKeyIdentifier: certificate.includeSubjectKeyIdentifier ?? false,
        includeAuthorityKeyIdentifier: certificate.includeAuthorityKeyIdentifier ?? false,
        includeExtendedKeyUsage: certificate.includeExtendedKeyUsage ?? false,
        hasPrivateKey : certificate.hasPrivateKey,
      };
    });
  }

  onFilter(filterDate: string): void {
    if (filterDate) {
      const date = new Date(filterDate);
      date.setHours(0, 0, 0, 0);
      this.currentFilterDate = date.getTime();
    } else {
      this.currentFilterDate = undefined;
    }
    this.currentPage = 0;
    this.loadCertificates(this.currentPage, this.currentSortBy, this.currentSortDir, this.currentFilterDate);
  }

  onClearFilter(): void {
    this.currentFilterDate = undefined;
    this.currentPage = 0;
    this.loadCertificates(this.currentPage, this.currentSortBy, this.currentSortDir);
  }

  onSort(event: { column: string; direction: string }): void {
    this.currentSortBy = event.column;
    this.currentSortDir = event.direction;
    this.currentPage = 0;
    this.loadCertificates(this.currentPage, this.currentSortBy, this.currentSortDir, this.currentFilterDate);
  }

  goToNextPage(): void {
    if (!this.isLastPage) {
      this.currentPage++;
      this.loadCertificates(this.currentPage, this.currentSortBy, this.currentSortDir, this.currentFilterDate);
    }
  }

  goToPreviousPage(): void {
    if (!this.isFirstPage) {
      this.currentPage--;
      this.loadCertificates(this.currentPage, this.currentSortBy, this.currentSortDir, this.currentFilterDate);
    }
  }

  onDownloadRequested(certificate: Certificate): void {
    this.selectedDownloadCertificate = certificate;
  }

  cancelDownload(): void {
    this.selectedDownloadCertificate = undefined;
  }

  submitDownloadWithKey(payload: { password: string; alias?: string }): void {
    if (!this.selectedDownloadCertificate || !payload.password) {
      return;
    }
    this.certificateService
      .downloadCertificate({
        serialNumber: this.selectedDownloadCertificate.serialNumber,
        keyStorePassword: payload.password,
        alias: payload.alias
      })
      .subscribe({
        next: (response) => {
          const fileName = this.resolveFileName(
            response.headers.get('content-disposition'),
            this.selectedDownloadCertificate?.serialNumber ?? '',
            "p12"

          );
          const blob = response.body ?? new Blob();
          this.triggerDownload(blob, fileName);
          this.cancelDownload();
        },
        error: (error) => {
          console.error('Error downloading certificate:', error);
        }
      });
  }

  submitDownloadWithoutKey(event: { format: 'PEM' | 'CER' }) {
    if(!this.selectedDownloadCertificate || !event.format){
      return
    }
    this.certificateService.downloadCertificateByFormat({
        serialNumber: this.selectedDownloadCertificate.serialNumber,
        format: event.format
    }).subscribe({
        next: (response) => {
          const fileName = this.resolveFileName(
            response.headers.get('content-disposition'),
            this.selectedDownloadCertificate?.serialNumber ?? '',
            event.format === 'PEM' ? 'pem' : 'cer'
          );
          const blob = response.body ?? new Blob();
          this.triggerDownload(blob, fileName);
          this.cancelDownload();
        },
        error: (error) => {
          console.error('Error downloading certificate:', error);
        }
      });
  }

  private resolveFileName(contentDisposition: string | null, serialNumber: string, format : string): string {
    if (contentDisposition) {
      const match = /filename="?([^";]+)"?/i.exec(contentDisposition);
      if (match?.[1]) {
        return match[1];
      }
    }
    return `cert_${serialNumber}.${format}`;
  }

  private triggerDownload(blob: Blob, fileName: string): void {
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    window.URL.revokeObjectURL(url);
  }
}
