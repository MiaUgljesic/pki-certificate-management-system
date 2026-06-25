import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Certificate } from '../../models/certificate-overview/Certificates';
import { Button } from '../button/button';
import { InputComponent } from '../input-component/input-component';

export type DownloadMode = 'with-key' | 'without-key';

@Component({
  selector: 'app-download-certificate-modal',
  imports: [CommonModule, Button, InputComponent],
  templateUrl: './download-certificate-modal.html',
  styleUrl: './download-certificate-modal.css'
})
export class DownloadCertificateModal {
  private _certificate?: Certificate;

  password: string = '';
  alias: string = '';
  format: 'PEM' | 'CER' = 'PEM';
  mode: 'with-key' | 'without-key' = 'without-key';

  @Input()
  set certificate(value: Certificate | undefined) {
    this._certificate = value;
    this.password = '';
    this.alias = '';
    this.format = 'PEM';
    this.mode = 'without-key';
  }
  get certificate(): Certificate | undefined {
    return this._certificate;
  }

  @Output() close = new EventEmitter<void>();
  @Output() submitWithKey = new EventEmitter<{ password: string; alias?: string }>();
  @Output() submitWithoutKey = new EventEmitter<{ format: 'PEM' | 'CER' }>();

  onClose(): void {
    this.close.emit();
  }

  allowWithKey(): boolean {
    return this.certificate?.certificateType === 'END_ENTITY' ? false : true;
  }

  setMode(mode : DownloadMode) : void {
    this.mode = mode;
    this.password = '';
    this.alias = '';
    this.format = 'PEM';
  }

  onSubmit(): void {
    if (this.mode === 'with-key') {
      if (!this.password){
        return;
      } 
      const trimmedAlias = this.alias.trim();
      this.submitWithKey.emit({password: this.password, alias: trimmedAlias.length > 0 ? trimmedAlias : undefined});
    } else {
      this.submitWithoutKey.emit({ format: this.format });
    }
  }

  get canSubmit(): boolean {
    if (this.mode === 'with-key'){
      return !!this.password;
    } 
    return true;
  }
}
