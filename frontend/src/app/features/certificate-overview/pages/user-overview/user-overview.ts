import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { CertificateService } from '../../../../core/services/certificate.service';
import { Certificate } from '../../../../shared/models/certificate-overview/Certificates';
import { PaginatedCertificateOverviewResponse } from '../../../../shared/models/certificate-overview/PaginatedCertificateOverviewResponse';
import { CertificateOverviewResponse } from '../../../../shared/models/certificate-overview/CertificateOverviewResponse';
import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { CommonModule, formatDate } from '@angular/common';
import { UserOverviewTable } from '../../components/user-overview-table/user-overview-table';
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
  selector: 'app-user-overview',
  imports: [PageHeader, CommonModule, UserOverviewTable, Button, DownloadCertificateModal],
  templateUrl: './user-overview.html',
  styleUrl: './user-overview.css',
})

export class UserOverview implements OnInit {
  allCertificates : Certificate[] = [];
  filteredCertificates : Certificate [] = [];

  currentPage: number = 0;
  pageSize: number = 8;
  totalPages: number = 0;
  totalElements: number = 0;
  isFirstPage: boolean = true;
  isLastPage: boolean = false;

  currentSortBy: string = 'serialNumber';
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
    this.certificateService.getUserCertificateOverview(page, this.pageSize, sortBy, sortDir, date).subscribe({
      next: (data: PaginatedCertificateOverviewResponse) => {
        this.allCertificates = this.transformCertificateData(data.content);
        this.filteredCertificates = [...this.allCertificates];
        this.currentPage = data.number;
        this.totalPages = data.totalPages;
        this.totalElements = data.totalElements;
        this.isFirstPage = data.first;
        this.isLastPage = data.last;
        this.cdr.detectChanges();
        console.log(this.allCertificates);
      },
      error: (error) => {
        console.error('Error loading certificate overview:', error);
      }
    });
  }

  transformCertificateData(apiData: CertificateOverviewResponse[]): Certificate[] {
  return apiData.map((certificate) => {
      return {
        id: certificate.id,
        serialNumber: certificate.serialNumber,
        commonName: certificate.commonName,
        organizationName: certificate.organizationName ?? 'N/A',
        organizationalUnit: certificate.organizationalUnit ?? 'N/A',
        country : certificate.country ?? 'N/A',
        email : certificate.email ?? 'N/A',
        status: certificate.status,
        validFrom: new Date(certificate.validFrom).toLocaleDateString('sr-RS'),
        validTo: new Date(certificate.validTo).toLocaleDateString('sr-RS'),
        keyAlgorithm : certificate.keyAlgorithm ?? 'N/A',
        keySize : certificate.keySize != null ? Number(certificate.keySize) : null,
        signatureAlgorithm : certificate.signatureAlgorithm ?? 'N/A',
        revocationReason: certificate.revocationReason ?(RevocationReasonLabels[certificate.revocationReason] || certificate.revocationReason):'N/A',
        revokedAt : certificate.revokedAt ? new Date(certificate.revokedAt).toLocaleDateString('sr-RS') : 'N/A',
        hasPrivateKey: certificate.hasPrivateKey,
        includeSubjectKeyIdentifier: certificate.includeSubjectKeyIdentifier,
        includeAuthorityKeyIdentifier: certificate.includeAuthorityKeyIdentifier,
        includeExtendedKeyUsage: certificate.includeExtendedKeyUsage,
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