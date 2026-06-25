import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { CommonModule } from '@angular/common';
import { User } from '../../../../shared/models/certificate-overview/User';
import { FormsModule } from '@angular/forms';
import { ProfileService } from '../../../../core/services/profile.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-user-profile',
  imports: [PageHeader, CommonModule, FormsModule],
  templateUrl: './user-profile.html',
  styleUrl: './user-profile.css',
})

export class UserProfile implements OnInit {
  user : User | null = null;
  showQrSetup : boolean = false;
  qrCodeUrl : string = '';
  verificationCode : string = '';
  message = '';
  showMessage = false;
  
  constructor(private profileService: ProfileService, private router: Router, private cdr: ChangeDetectorRef) { }

  ngOnInit(){
    this.loadUserProfile();
  }

  loadUserProfile() {
    this.profileService.getProfile().subscribe({
      next: (data : User) => {
        this.user = data;
        this.cdr.detectChanges();
      },
      error: (error: any) => {
        this.showMessageToast("There was an error loading user information");
        this.user = null;
      }
    });
  }

  onTwoFactorToggle(event: any) : void{
    const isChecked : boolean = event.target.checked;
    if(isChecked){
      this.profileService.generateQrCode().subscribe({
        next: (data : any) => {
          this.qrCodeUrl = data.qrCodeUrl;
          this.showQrSetup = true;
          this.cdr.detectChanges();
        },
        error: (error: any) => {
          this.showMessageToast("There was an error generating QR code");
          this.showQrSetup = false;
          event.target.checked = false;
          if(this.user){
            this.user.isTwoFactorEnabled = false;
          }
          this.cdr.detectChanges();
        }
      });
    }
    else{
      this.profileService.disableTwoFactor().subscribe({
        next: () => {
          this.showQrSetup = false;
          if (this.user) {
            this.user.isTwoFactorEnabled = false;
          }
          this.showMessageToast('Two-factor authentication successfully disabled!');
          this.cdr.detectChanges();
        },
        error: (error: any) => {
          this.showMessageToast("There was an error disabling two factor authentication")
          this.showQrSetup = true;
          event.target.checked = true;
          this.cdr.detectChanges();
        }
      });
    }
  }

  verifyAndEnable2FA(){
    if(this.verificationCode.length === 6){
      this.profileService.verifyTwoFactor(this.verificationCode).subscribe({
        next: (data : any) => {
          if (this.user) {
            this.user.isTwoFactorEnabled = true;
          }
          this.showQrSetup = false;
          this.verificationCode = '';
          this.showMessageToast('Two-factor authentication successfully enabled!');
        },
        error: (error: any) => {
          this.showMessageToast('Invalid code. Please try again.');
          this.verificationCode = ''; 
          this.showQrSetup = true;
          this.cdr.detectChanges();
          if (this.user) {
            this.user.isTwoFactorEnabled = false;
            this.cdr.detectChanges();
          }
        }
      });
    }
  }

  goBack(){
    this.router.navigate(['/user-certificate-overview']);
  }

  showMessageToast(message: string): void {
    this.message = message;
    this.showMessage = true;
    this.cdr.detectChanges();
    setTimeout(() => { this.showMessage = false; }, 3000);
    this.cdr.detectChanges();
  }
}