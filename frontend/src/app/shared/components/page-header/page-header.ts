import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-page-header',
  imports: [FormsModule, CommonModule],
  templateUrl: './page-header.html',
  styleUrl: './page-header.css',
})
export class PageHeader {
  @Input() title: string = '';
  @Input() description: string = '';
  @Input() showDateFilter: boolean = false;
  @Input() searchMode: boolean = false;
  @Input() searchPlaceholder: string = '';

  @Output() filter = new EventEmitter<string>();
  @Output() clearFilter = new EventEmitter<void>();

  filterDate: string = '';

  onFilter(): void {
    this.filter.emit(this.filterDate);
  }

  onClearFilter(): void {
    this.filterDate = '';
    this.clearFilter.emit();
  }
}

