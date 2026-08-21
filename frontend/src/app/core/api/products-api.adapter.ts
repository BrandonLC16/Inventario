import { Injectable, inject } from '@angular/core';

import { FindAll4RequestParams, ProductsService } from './generated/api/products.service';
import { ProductRequest } from './generated/model/product-request';

@Injectable({ providedIn: 'root' })
export class ProductsApiAdapter {
  private readonly productsApi = inject(ProductsService);

  list(request: FindAll4RequestParams = {}) {
    return this.productsApi.findAll4(request);
  }

  get(id: string) {
    return this.productsApi.findById2({ id });
  }

  create(productRequest: ProductRequest) {
    return this.productsApi.create4({ productRequest });
  }

  update(id: string, productRequest: ProductRequest) {
    return this.productsApi.update2({ id, productRequest });
  }

  delete(id: string) {
    return this.productsApi._delete({ id });
  }
}
