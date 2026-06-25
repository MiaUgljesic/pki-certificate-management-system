import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container position-fixed top-0 end-0 p-3" style="z-index: 11">
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="toast show"
          [class.toast-success]="toast.type === 'success'"
          [class.toast-error]="toast.type === 'error'"
          [class.toast-info]="toast.type === 'info'"
          [class.toast-warning]="toast.type === 'warning'"
          role="alert"
        >
          <div class="toast-header">
            <strong class="me-auto">{{ toast.type | uppercase }}</strong>
            <button
              type="button"
              class="btn-close"
              (click)="toastService.remove(toast.id)"
              aria-label="Close"
            ></button>
          </div>
          <div class="toast-body">
            {{ toast.message }}
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .toast-success {
      background-color: #d4edda;
      border: 1px solid #c3e6cb;
    }

    .toast-success .toast-header {
      background-color: #c3e6cb;
      color: #155724;
    }

    .toast-success .toast-body {
      color: #155724;
    }

    .toast-error {
      background-color: #f8d7da;
      border: 1px solid #f5c6cb;
    }

    .toast-error .toast-header {
      background-color: #f5c6cb;
      color: #721c24;
    }

    .toast-error .toast-body {
      color: #721c24;
    }

    .toast-info {
      background-color: #d1ecf1;
      border: 1px solid #bee5eb;
    }

    .toast-info .toast-header {
      background-color: #bee5eb;
      color: #0c5460;
    }

    .toast-info .toast-body {
      color: #0c5460;
    }

    .toast-warning {
      background-color: #fff3cd;
      border: 1px solid #ffeaa7;
    }

    .toast-warning .toast-header {
      background-color: #ffeaa7;
      color: #856404;
    }

    .toast-warning .toast-body {
      color: #856404;
    }
  `
})
export class ToastContainerComponent {
  constructor(readonly toastService: ToastService) {}
}
