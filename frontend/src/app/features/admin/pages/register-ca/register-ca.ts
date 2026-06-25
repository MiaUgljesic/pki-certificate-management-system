import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RegisterCAFormComponent } from '../../components/register-ca-form/register-ca-form';

@Component({
  selector: 'app-register-ca',
  standalone: true,
  imports: [CommonModule, RegisterCAFormComponent],
  templateUrl: './register-ca.html',
  styleUrls: ['./register-ca.css']
})
export class RegisterCAComponent {}
