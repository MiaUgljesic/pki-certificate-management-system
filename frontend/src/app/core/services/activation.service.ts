import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';


export interface ActivationChallengeRequest {
  token: string;
  decryptedChallenge: string;
}


export interface ActivationChallengeResponse {
  success?: boolean;
  message?: string;
  [key: string]: any;
}


@Injectable({
  providedIn: 'root'
})
export class ActivationService {
    private readonly apiUrl = `${environment.apiUrl}/auth/activate-challenge`;
  
    constructor(private http: HttpClient) {}

    activateChallenge(token: string, decryptedChallenge: string): Observable<string> {
        const payload: ActivationChallengeRequest = {
        token: token,
        decryptedChallenge: decryptedChallenge
        };
        return this.http.post(this.apiUrl, payload, {
            responseType: 'text'
        });
    }
}
