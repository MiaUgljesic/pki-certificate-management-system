import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivationService } from '../../../core/services';

@Component({
  selector: 'app-activate-challenge',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './activate-challenge.html',
  styleUrls: ['./activate-challenge.css']
})
export class ActivateChallengeComponent implements OnInit {
  token: string = '';
  challenge: string = '';
  privateKeyPem: string = '';
  selectedFileName: string = '';
  loading: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private activationService: ActivationService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => this.token = params['token']);
    this.route.queryParams.subscribe(params => this.challenge = params['challenge']);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      this.selectedFileName = file.name;
      console.log('File selected:', file.name);
      console.log('File size:', file.size, 'bytes');
      const reader = new FileReader();
      reader.onload = (e) => {
        this.privateKeyPem = e.target?.result as string;
        console.log('File read successfully');
        console.log('File content (first 150 chars):', this.privateKeyPem.substring(0, 150));
        console.log('Total file content length:', this.privateKeyPem.length, 'characters');
      };
      reader.readAsText(file);
    }
  }

  clearFileSelection(): void {
    this.selectedFileName = '';
    this.privateKeyPem = '';
    const fileInput = document.getElementById('privateKeyFile') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
    console.log('File selection cleared');
  }

  async activateAccount(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    try {
      console.log('=== ACTIVATION PROCESS STARTED ===');
      console.log('Token:', this.token);
      console.log('Challenge (first 50 chars):', this.challenge.substring(0, 50));
      console.log('Private Key PEM (first 50 chars):', this.privateKeyPem.substring(0, 50));

      if (!this.token || !this.challenge || !this.privateKeyPem) {
        throw new Error('Missing token, challenge, or private key');
      }

      console.log('Step 1: All inputs present ✓');

      console.log('Step 2: Importing private key...');
      const privateKey = await this.importPrivateKey(this.privateKeyPem);
      console.log('Step 2: Private key imported successfully ✓');

      console.log('Step 3: Converting challenge base64 to ArrayBuffer...');
      const encryptedBuffer = this.base64ToArrayBuffer(this.challenge);
      console.log('Step 3: Challenge converted to buffer, length:', encryptedBuffer.byteLength, 'bytes');

      console.log('Step 4: Decrypting challenge with RSA-OAEP (SHA-256)...');
      const decryptedBuffer = await window.crypto.subtle.decrypt(
        {
          name: 'RSA-OAEP'
        },
        privateKey,
        encryptedBuffer
      );
      console.log('Step 4: Decryption successful ✓');
      console.log('Decrypted buffer length:', decryptedBuffer.byteLength, 'bytes');

      console.log('Step 5: Converting decrypted buffer to text...');
      const decryptedChallenge = new TextDecoder().decode(decryptedBuffer);
      console.log('Step 5: Decrypted challenge text:', decryptedChallenge);

      console.log('Step 6: Sending activation request to backend...');
      await this.activationService.activateChallenge(this.token, decryptedChallenge).toPromise();
      console.log('Step 6: Backend activation successful ✓');

      this.successMessage = 'Account activated successfully! Redirecting to login...';
      console.log('=== ACTIVATION PROCESS COMPLETED SUCCESSFULLY ===');
      setTimeout(() => {
        this.router.navigate(['/login']);
      }, 2000);
    } catch (error) {
      console.error('=== ACTIVATION PROCESS FAILED ===');
      console.error('Error:', error);
      console.error('Error type:', error instanceof Error ? error.constructor.name : typeof error);
      this.errorMessage = `Activation failed: ${error instanceof Error ? error.message : String(error)}`;
    } finally {
      this.loading = false;
    }
  }

  private async importPrivateKey(pemString: string): Promise<CryptoKey> {
    try {
      console.log('=== PRIVATE KEY IMPORT PROCESS ===');
      console.log('Raw Private Key Text from file (first 100 chars):', pemString.substring(0, 100));

      const pemHeader = '-----BEGIN PRIVATE KEY-----';
      const pemFooter = '-----END PRIVATE KEY-----';
      const headerIndex = pemString.indexOf(pemHeader);
      const footerIndex = pemString.indexOf(pemFooter);

      console.log('PEM Header found at index:', headerIndex);
      console.log('PEM Footer found at index:', footerIndex);

      if (headerIndex === -1 || footerIndex === -1) {
        throw new Error('Invalid PEM format: Missing BEGIN or END markers');
      }

      console.log('Extracting base64 content between markers...');
      let pemContents = pemString.substring(
        headerIndex + pemHeader.length,
        footerIndex
      );

      console.log('Raw extracted content (first 100 chars):', pemContents.substring(0, 100));

      pemContents = pemContents
        .trim()
        .replace(/\r/g, '')
        .replace(/\n/g, '')
        .replace(/\s/g, '');

      console.log('Cleaned base64 content (first 100 chars):', pemContents.substring(0, 100));
      console.log('Cleaned base64 length:', pemContents.length, 'characters');
      console.log('Sanitized Challenge Base64:', pemContents);

      console.log('Converting base64 to binary...');
      const binaryString = atob(pemContents);
      console.log('Binary string length:', binaryString.length, 'bytes');

      console.log('Converting binary to Uint8Array...');
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      console.log('Uint8Array created, length:', bytes.length);

      console.log('Attempting to import private key...');
      console.log('Import algorithm:', {
        name: 'RSA-OAEP',
        hash: 'SHA-256'
      });

      try {
        const importedKey = await window.crypto.subtle.importKey(
          'pkcs8',
          bytes.buffer,
          {
            name: 'RSA-OAEP',
            hash: 'SHA-256'
          },
          false,
          ['decrypt']
        );
        console.log('Key imported successfully ✓');
        console.log('Key type:', importedKey.type);
        console.log('Key algorithm:', importedKey.algorithm);
        console.log('=== PRIVATE KEY IMPORT SUCCESSFUL ===');
        return importedKey;
      } catch (importError) {
        console.error('CRITICAL: Key import failed inside browser:', importError);
        console.error('Import error name:', (importError as Error).name);
        console.error('Import error message:', (importError as Error).message);
        throw new Error(`WebCrypto importKey failed: ${(importError as Error).message}`);
      }
    } catch (error) {
      console.error('=== PRIVATE KEY IMPORT FAILED ===');
      console.error('Error:', error);
      throw error;
    }
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    try {
      console.log('=== BASE64 TO ARRAY BUFFER CONVERSION ===');
      console.log('Input base64 (first 100 chars):', base64.substring(0, 100));
      console.log('Base64 string length:', base64.length, 'characters');

      console.log('Decoding base64 to binary string...');
      const binaryString = atob(base64);
      console.log('Binary string length:', binaryString.length, 'bytes');

      console.log('Creating Uint8Array...');
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      console.log('Uint8Array created, length:', bytes.length);
      console.log('ArrayBuffer conversion successful ✓');
      console.log('=== CONVERSION COMPLETE ===');

      return bytes.buffer;
    } catch (error) {
      console.error('CRITICAL: Base64 conversion failed:', error);
      throw error;
    }
  }
}
