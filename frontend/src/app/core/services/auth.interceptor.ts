import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { BehaviorSubject, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from './auth.service';

let isRefreshing = false;
const refreshSubject = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const isRefreshRequest = req.url.includes('/auth/refresh');
  const accessToken = authService.getAccessToken();

  if (!accessToken) {
    return next(req);
  }

  const authReq = accessToken && !isRefreshRequest ? req.clone({
    setHeaders: {
      Authorization: `Bearer ${accessToken}`
    }
  }) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isRefreshRequest) {
        if (error.status === 401) {
          console.warn('[auth] 401 on refresh request, skipping retry', {
            url: req.url,
            status: error.status
          });
        }
        return throwError(() => error);
      }

      const refreshToken = authService.getRefreshToken();
      if (!refreshToken) {
        console.warn('[auth] Missing refresh token, logging out');
        authService.logout();
        return throwError(() => error);
      }

      if (!isRefreshing) {
        isRefreshing = true;
        refreshSubject.next(null);
        console.info('[auth] Access token expired, attempting refresh');

        return authService.refresh().pipe(
          switchMap((response) => {
            isRefreshing = false;
            const accessTokenValue = response.accessToken ?? (response as any).access_token;
            const refreshTokenValue = response.refreshToken ?? (response as any).refresh_token;

            if (!accessTokenValue || !refreshTokenValue) {
              console.error('[auth] Refresh response missing tokens', response);
              throw new Error('Refresh response missing tokens');
            }

            authService.setAccessToken(accessTokenValue);
            authService.setRefreshToken(refreshTokenValue);
            refreshSubject.next(accessTokenValue);

            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${accessTokenValue}`
              }
            });

            return next(retryReq);
          }),
          catchError((refreshError) => {
            isRefreshing = false;
            const status = refreshError?.status;
            console.error('[auth] Refresh failed', {
              status,
              message: refreshError?.message
            });

            if (status === 401 || status === 403) {
              authService.logout();
            }
            return throwError(() => refreshError);
          })
        );
      }

      return refreshSubject.pipe(
        filter((token): token is string => !!token),
        take(1),
        switchMap((token) => {
          const retryReq = req.clone({
            setHeaders: {
              Authorization: `Bearer ${token}`
            }
          });

          return next(retryReq);
        })
      );
    })
  );
};
