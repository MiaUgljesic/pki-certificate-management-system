import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { vi } from 'vitest';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: {
    getAccessToken: ReturnType<typeof vi.fn>;
    getRefreshToken: ReturnType<typeof vi.fn>;
    refresh: ReturnType<typeof vi.fn>;
    setAccessToken: ReturnType<typeof vi.fn>;
    setRefreshToken: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authService = {
      getAccessToken: vi.fn(),
      getRefreshToken: vi.fn(),
      refresh: vi.fn(),
      setAccessToken: vi.fn(),
      setRefreshToken: vi.fn(),
      logout: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService }
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should refresh and retry on 401', () => {
    authService.getAccessToken.mockReturnValue('oldAccess');
    authService.getRefreshToken.mockReturnValue('refresh1');
    authService.refresh.mockReturnValue(of({
      accessToken: 'newAccess',
      refreshToken: 'newRefresh',
      tokenType: 'Bearer'
    }));

    httpClient.get('/api/certificates/all').subscribe();

    const first = httpMock.expectOne('/api/certificates/all');
    expect(first.request.headers.get('Authorization')).toBe('Bearer oldAccess');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.refresh).toHaveBeenCalled();

    const retried = httpMock.expectOne('/api/certificates/all');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer newAccess');
    retried.flush({});

    expect(authService.setAccessToken).toHaveBeenCalledWith('newAccess');
    expect(authService.setRefreshToken).toHaveBeenCalledWith('newRefresh');
  });
});
