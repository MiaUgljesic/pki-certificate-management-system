import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';
import { Certificate } from '../../../../shared/models/certificate-overview/Certificates';
import { Router } from '@angular/router';
import { Button } from '../../../../shared/components/button/button';

type SortColumn = 'serialNumber'| 'commonName' | 'organization' | 'status' | 'validFrom' | 'validTo' | 'issuerSerialNumber' | 'certificateType';

@Component({
  selector: 'app-admin-certificates-table',
  imports: [CommonModule, Button],
  templateUrl: './admin-certificates-table.html',
  styleUrl: './admin-certificates-table.css',
})

export class AdminCertificatesTable {
  private _certificates: Certificate[] = [];

  constructor(private router: Router, private cdr: ChangeDetectorRef) {}

  @Input()
  set certificates(value: Certificate[]) {
    this._certificates  = value ?? [];
  }
  get certificates(): Certificate[] {
    return this._certificates;
  }
  @Output() sortChange = new EventEmitter<{ column: string; direction: string }>();
  @Output() downloadRequested = new EventEmitter<Certificate>();

  currentSortColumn: string = '';
  currentSortDirection: 'asc' | 'desc' = 'asc';

  sort(column: string): void {
    if (this.currentSortColumn === column) {
      this.currentSortDirection = this.currentSortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.currentSortColumn = column;
      this.currentSortDirection = 'asc';
    }
    this.sortChange.emit({column: this.currentSortColumn, direction: this.currentSortDirection});
  }

  getSortIcon(column: SortColumn): string {
    if (this.currentSortColumn !== column) {
      return '⇅';
    }
    return this.currentSortDirection === 'asc' ? '↑' : '↓';
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'badge bg-success';
      case 'REVOKED': return 'badge bg-danger';
      case 'EXPIRED': return 'badge bg-secondary';
      default: return 'badge bg-secondary';
    }
  }

  viewCertificateDetails(certificate: Certificate): void {
    this.router.navigate(['/overview-certificate-details', certificate.id], { state: { certificate } });
  }

  onDownloadClick(certificate: Certificate): void {
    this.downloadRequested.emit(certificate);
  }
}
