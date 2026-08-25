import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';

import { FindAll2RequestParams, SuppliersService } from './generated/api/suppliers.service';
import { SupplierRequest } from './generated/model/supplier-request';

@Injectable({ providedIn: 'root' })
export class SuppliersApiAdapter {
  private readonly suppliersApi = inject(SuppliersService);

  list(request: FindAll2RequestParams = {}) {
    return this.suppliersApi.findAll2(request);
  }

  get(id: string) {
    return this.suppliersApi.findById1({ id });
  }

  create(supplierRequest: SupplierRequest) {
    return this.suppliersApi.create2({ supplierRequest });
  }

  update(id: string, supplierRequest: SupplierRequest) {
    return this.suppliersApi.update1({ id, supplierRequest });
  }

  deactivate(id: string) {
    return this.suppliersApi.deactivate1({ id }, 'response').pipe(
      map((response) => {
        if (response.status !== 204) {
          throw new Error(`Unexpected supplier deactivation status: ${response.status}`);
        }
      }),
    );
  }
}
