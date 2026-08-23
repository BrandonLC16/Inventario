import { Injectable, inject } from '@angular/core';

import { FindAllRequestParams, WarehousesService } from './generated/api/warehouses.service';
import { WarehouseRequest } from './generated/model/warehouse-request';

@Injectable({ providedIn: 'root' })
export class WarehousesApiAdapter {
  private readonly warehousesApi = inject(WarehousesService);

  list(request: FindAllRequestParams = {}) {
    return this.warehousesApi.findAll(request);
  }

  get(id: string) {
    return this.warehousesApi.findById({ id });
  }

  create(warehouseRequest: WarehouseRequest) {
    return this.warehousesApi.create({ warehouseRequest });
  }

  update(id: string, warehouseRequest: WarehouseRequest) {
    return this.warehousesApi.update({ id, warehouseRequest });
  }

  deactivate(id: string) {
    return this.warehousesApi.deactivate({ id });
  }
}
