import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import { FindAll10RequestParams, InventoryService } from './generated/api/inventory.service';
import { WarehouseInventoryService } from './generated/api/warehouse-inventory.service';
import { InventoryResponse } from './generated/model/inventory-response';
import { InventorySettingResponse } from './generated/model/inventory-setting-response';
import { PageResponseInventoryResponse } from './generated/model/page-response-inventory-response';

export const MAIN_WAREHOUSE_ID = '00000000-0000-0000-0000-000000000001';

export interface InventoryBalanceRow {
  readonly balance: InventoryResponse;
  readonly product: InventorySettingResponse;
}

export interface InventoryBalancePage {
  readonly response: PageResponseInventoryResponse;
  readonly rows: readonly InventoryBalanceRow[];
}

@Injectable({ providedIn: 'root' })
export class InventoryApiAdapter {
  private readonly inventoryApi = inject(InventoryService);
  private readonly warehouseInventoryApi = inject(WarehouseInventoryService);

  listMain(request: FindAll10RequestParams = {}): Observable<InventoryBalancePage> {
    return this.composePage(MAIN_WAREHOUSE_ID, request, this.inventoryApi.findAll10(request));
  }

  listWarehouse(
    warehouseId: string,
    request: FindAll10RequestParams = {},
  ): Observable<InventoryBalancePage> {
    return this.composePage(
      warehouseId,
      request,
      this.warehouseInventoryApi.findAll9({ warehouseId, ...request }),
    );
  }

  private composePage(
    warehouseId: string,
    request: FindAll10RequestParams,
    balances: Observable<PageResponseInventoryResponse>,
  ): Observable<InventoryBalancePage> {
    return forkJoin({
      balances,
      metadata: this.warehouseInventoryApi.findSettings({ warehouseId, ...request }),
    }).pipe(
      map(({ balances: balancePage, metadata }) => {
        const balancesContent = balancePage.content ?? [];
        const metadataContent = metadata.content ?? [];

        if (
          balancePage.totalElements !== undefined &&
          metadata.totalElements !== undefined &&
          balancePage.totalElements !== metadata.totalElements
        ) {
          throw new Error('Inventory and product metadata totals do not match.');
        }

        const metadataByProductId = new Map<string, InventorySettingResponse>();
        for (const product of metadataContent) {
          if (
            !product.productId ||
            product.warehouseId !== warehouseId ||
            !product.sku?.trim() ||
            !product.name?.trim() ||
            metadataByProductId.has(product.productId)
          ) {
            throw new Error('Inventory product metadata is incomplete or inconsistent.');
          }
          metadataByProductId.set(product.productId, product);
        }

        const rows = balancesContent.map((balance): InventoryBalanceRow => {
          this.validateBalance(balance, warehouseId);
          const product = metadataByProductId.get(balance.productId!);
          if (!product) {
            throw new Error('Inventory product metadata is missing.');
          }
          return { balance, product };
        });

        if (metadataByProductId.size !== rows.length) {
          throw new Error('Inventory and product metadata pages do not contain the same products.');
        }

        return { response: balancePage, rows };
      }),
    );
  }

  private validateBalance(balance: InventoryResponse, warehouseId: string): void {
    const quantity = balance.quantity;
    const reserved = balance.reservedQuantity;
    const available = balance.availableQuantity;

    if (
      !balance.productId ||
      balance.warehouseId !== warehouseId ||
      !Number.isSafeInteger(quantity) ||
      !Number.isSafeInteger(reserved) ||
      !Number.isSafeInteger(available) ||
      quantity! < 0 ||
      reserved! < 0 ||
      available! < 0 ||
      available !== quantity! - reserved!
    ) {
      throw new Error('Inventory balance is incomplete or inconsistent.');
    }
  }
}
