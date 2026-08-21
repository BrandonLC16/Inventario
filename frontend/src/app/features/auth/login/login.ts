import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiErrorService, ApiProblem } from '../../../core/http/api-error.service';
import { RetryAfterTracker } from '../../../core/http/retry-after-tracker';
import { SessionService } from '../../../core/session/session.service';
import { ApiErrorMessage } from '../../../shared/api-error-message/api-error-message';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ApiErrorMessage,
  ],
  providers: [RetryAfterTracker],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly apiErrors = inject(ApiErrorService);

  protected readonly submitting = signal(false);
  protected readonly localErrorMessage = signal<string | null>(null);
  protected readonly apiProblem = signal<ApiProblem | null>(null);
  protected readonly retryAfter = inject(RetryAfterTracker);
  protected readonly form = this.formBuilder.nonNullable.group({
    identifier: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    if (this.submitting() || this.retryAfter.blocked()) {
      return;
    }

    this.localErrorMessage.set(null);
    this.apiProblem.set(null);
    this.apiErrors.clearValidationErrors(this.form);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.localErrorMessage.set('Revisa los campos obligatorios antes de continuar.');
      return;
    }

    this.submitting.set(true);

    this.session
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(this.safeReturnUrl()),
        error: (error: unknown) => {
          this.form.controls.password.reset('');
          const problem = this.apiErrors.from(error);
          this.apiErrors.applyValidationErrors(this.form, problem);
          this.retryAfter.block(problem.retryAfterSeconds);
          this.apiProblem.set(problem);
        },
      });
  }

  private safeReturnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (
      returnUrl?.startsWith('/') &&
      !returnUrl.startsWith('//') &&
      !returnUrl.startsWith('/login')
    ) {
      return returnUrl;
    }
    return '/dashboard';
  }
}
