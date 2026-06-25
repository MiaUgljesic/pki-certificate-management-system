import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLinkWithHref } from '@angular/router';
import { Button } from '../button/button';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLinkWithHref, Button, CommonModule, FormsModule],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css'
})
export class NavbarComponent implements OnInit, OnDestroy {

  private readonly destroy$ = new Subject<void>();
  protected loggedIn = signal(false);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) { }


  ngOnInit(): void {
    this.loggedIn.set(this.authService.isLoggedIn());
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        setTimeout(() => {
          this.loggedIn.set(this.authService.isLoggedIn());
        });
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  protected menuOpen = signal(false);

  protected toggleMenu(): void {
    this.menuOpen.set(!this.menuOpen());
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  canIssueCertificates(): boolean {
    const role = this.authService.getUserRole();
    return role === 'ADMIN' || role === 'CA_USER';
  }

  isAdmin(): boolean {
    const role = this.authService.getUserRole();
    return role === 'ADMIN';
  }

  isCAUser(): boolean {
    const role = this.authService.getUserRole();
    return role === 'CA_USER';
  }

  isRegularUser(): boolean {
    const role = this.authService.getUserRole();
    return role === 'USER';
  }

  isLoggedIn(): boolean {
    return this.authService.isLoggedIn();
  }

  message = '';
  showMessage = false;



  logout() {
    this.authService.logout();
    this.loggedIn.set(false);
    this.closeMenu();
    this.router.navigate(['/login']);
    this.showMessageToast('Successfully logged out.');
  }

  showMessageToast(message: string): void {
    this.message = message;
    this.showMessage = true;
    setTimeout(() => {
      this.showMessage = false;
    }, 3000);
  }
}
