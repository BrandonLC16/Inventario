import { Injectable, inject } from '@angular/core';

import { FindAll4RequestParams, ProductsService } from './generated/api/products.service';

@Injectable({ providedIn: 'root' })
export class ProductsApiAdapter {
  private readonly productsApi = inject(ProductsService);

  list(request: FindAll4RequestParams = {}) {
    return this.productsApi.findAll4(request);
  }
}
