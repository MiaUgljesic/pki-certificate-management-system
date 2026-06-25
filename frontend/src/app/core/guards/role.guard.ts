import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  const requiredRole = route.data['requiredRole'];
  const requiredRoles: string[] | undefined = route.data['requiredRoles'];
  const accessToken = localStorage.getItem('accessToken');
  const userRole = localStorage.getItem('userRole');

  // Check if token exists
  if (!accessToken) {
    router.navigate(['/login']);
    return false;
  }

  // Check if user has required role(s)
  if (requiredRoles && (!userRole || !requiredRoles.includes(userRole))) {
    router.navigate(['/login']);
    return false;
  }

  if (requiredRole && userRole !== requiredRole) {
    router.navigate(['/login']);
    return false;
  }

  return true;
};
