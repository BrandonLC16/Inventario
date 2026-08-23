import { Injectable, inject } from '@angular/core';
import { HttpContext } from '@angular/common/http';
import { Observable, concatMap, forkJoin, map } from 'rxjs';

import {
  FindAll10RequestParams,
  FindAllMovementsRequestParams,
  FindLowStock1RequestParams,
  InventoryService,
} from './generated/api/inventory.service';
import {
  FindSettingsRequestParams,
  WarehouseInventoryService,
} from './generated/api/warehouse-inventory.service';
import { InventoryResponse } from './generated/model/inventory-response';
import { InventorySettingRequest } from './generated/model/inventory-setting-request';
import { InventorySettingResponse } from './generated/model/inventory-setting-response';
import { LowStockResponse } from './generated/model/low-stock-response';
import { PageResponseInventoryResponse } from './generated/model/page-response-inventory-response';
import { PageResponseInventorySettingResponse } from './generated/model/page-response-inventory-setting-response';
import { PageResponseLowStockResponse } from './generated/model/page-response-low-stock-response';
import { StockAdjustmentRequest } from './generated/model/stock-adjustment-request';
import { StockMovementPageResponse } from './generated/model/stock-movement-page-response';
import { StockMovementResponse } from './generated/model/stock-movement-response';
import { DISABLE_AUTH_REPLAY } from '../session/session.interceptor';

export const MAIN_WAREHOUSE_ID = '00000000-0000-0000-0000-000000000001';

const MOVEMENT_TYPES = new Set<string>([
  'INITIAL_STOCK',
  'MANUAL_IN',
  'MANUAL_OUT',
  'ORDER_RESERVED',
  'ORDER_RESERVATION_RELEASED',
  'ORDER_CONFIRMED',
  'ORDER_CANCELLED',
  'PURCHASE_RECEIVED',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'PHYSICAL_COUNT_ADJUSTMENT',
]);

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

  listSettings(
    warehouseId: string,
    request: Omit<FindSettingsRequestParams, 'warehouseId'> = {},
  ): Observable<PageResponseInventorySettingResponse> {
    return this.warehouseInventoryApi.findSettings({ warehouseId, ...request }).pipe(
      map((response) => {
        const productIds = new Set<string>();
        for (const setting of response.content ?? []) {
          this.validateSetting(setting, warehouseId);
          if (productIds.has(setting.productId!)) {
            throw new Error('Warehouse settings contain a duplicate product.');
          }
          productIds.add(setting.productId!);
        }
        return response;
      }),
    );
  }

  getSetting(warehouseId: string, productId: string): Observable<InventorySettingResponse> {
    return this.warehouseInventoryApi
      .findSetting({ warehouseId, productId })
      .pipe(map((setting) => this.validateSetting(setting, warehouseId, productId)));
  }

  configureSetting(
    warehouseId: string,
    productId: string,
    inventorySettingRequest: InventorySettingRequest,
  ): Observable<InventorySettingResponse> {
    return this.warehouseInventoryApi
      .configure({ warehouseId, productId, inventorySettingRequest })
      .pipe(concatMap(() => this.getSetting(warehouseId, productId)));
  }

  adjustStock(
    warehouseId: string,
    productId: string,
    stockAdjustmentRequest: StockAdjustmentRequest,
  ): Observable<InventoryResponse> {
    const context = new HttpContext().set(DISABLE_AUTH_REPLAY, true);
    const response =
      warehouseId === MAIN_WAREHOUSE_ID
        ? this.inventoryApi.adjust1({ productId, stockAdjustmentRequest }, 'body', false, {
            context,
          })
        : this.warehouseInventoryApi.adjust(
            { warehouseId, productId, stockAdjustmentRequest },
            'body',
            false,
            { context },
          );
    return response.pipe(map((balance) => this.validateBalance(balance, warehouseId, productId)));
  }

  listLowStock(
    warehouseId: string,
    request: FindLowStock1RequestParams = {},
  ): Observable<PageResponseLowStockResponse> {
    const response =
      warehouseId === MAIN_WAREHOUSE_ID
        ? this.inventoryApi.findLowStock1(request)
        : this.warehouseInventoryApi.findLowStock({ warehouseId, ...request });
    return response.pipe(
      map((page) => {
        const productIds = new Set<string>();
        for (const alert of page.content ?? []) {
          const productId = this.validateLowStock(alert, warehouseId);
          if (productIds.has(productId)) {
            throw new Error('Low-stock alerts contain a duplicate product.');
          }
          productIds.add(productId);
        }
        return page;
      }),
    );
  }

  listMovements(
    warehouseId: string,
    request: FindAllMovementsRequestParams = {},
  ): Observable<StockMovementPageResponse> {
    const response =
      warehouseId === MAIN_WAREHOUSE_ID
        ? this.inventoryApi.findAllMovements(request)
        : this.warehouseInventoryApi.findMovements({ warehouseId, ...request });
    return response.pipe(
      map((page) => {
        const movementIds = new Set<string>();
        for (const movement of page.content ?? []) {
          const movementId = this.validateMovement(movement, warehouseId);
          if (movementIds.has(movementId)) {
            throw new Error('Stock movement page contains a duplicate movement.');
          }
          movementIds.add(movementId);
        }
        return page;
      }),
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

  private validateBalance(
    balance: InventoryResponse,
    warehouseId: string,
    productId?: string,
  ): InventoryResponse {
    const quantity = balance.quantity;
    const reserved = balance.reservedQuantity;
    const available = balance.availableQuantity;

    if (
      !balance.productId ||
      balance.warehouseId !== warehouseId ||
      (productId !== undefined && balance.productId !== productId) ||
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
    return balance;
  }

  private validateSetting(
    setting: InventorySettingResponse,
    warehouseId: string,
    productId?: string,
  ): InventorySettingResponse {
    if (
      !setting.productId ||
      setting.warehouseId !== warehouseId ||
      (productId !== undefined && setting.productId !== productId) ||
      !setting.sku?.trim() ||
      !setting.name?.trim() ||
      !Number.isSafeInteger(setting.minimumStock) ||
      setting.minimumStock! < 0 ||
      typeof setting.active !== 'boolean'
    ) {
      throw new Error('Warehouse inventory setting is incomplete or inconsistent.');
    }
    return setting;
  }

  private validateLowStock(alert: LowStockResponse, warehouseId: string): string {
    const quantity = alert.quantity;
    const reserved = alert.reservedQuantity;
    const available = alert.availableQuantity;
    const minimum = alert.minimumStock;
    const replenishment = alert.replenishmentQuantity;
    if (
      !alert.productId ||
      alert.warehouseId !== warehouseId ||
      !alert.sku?.trim() ||
      !alert.name?.trim() ||
      !Number.isSafeInteger(quantity) ||
      !Number.isSafeInteger(reserved) ||
      !Number.isSafeInteger(available) ||
      !Number.isSafeInteger(minimum) ||
      !Number.isSafeInteger(replenishment) ||
      quantity! < 0 ||
      reserved! < 0 ||
      available! < 0 ||
      minimum! < 0 ||
      available !== quantity! - reserved! ||
      replenishment !== Math.max(0, minimum! - available!) ||
      (alert.alert === 'OUT_OF_STOCK'
        ? available !== 0
        : alert.alert !== 'LOW_STOCK' || available === 0 || available! >= minimum!)
    ) {
      throw new Error('Low-stock alert is incomplete or inconsistent.');
    }
    return alert.productId;
  }

  private validateMovement(movement: StockMovementResponse, warehouseId: string): string {
    const quantityDelta = movement.quantityDelta;
    const balanceBefore = movement.balanceBefore;
    const balanceAfter = movement.balanceAfter;
    const reservationDelta = movement.reservationDelta;
    const reservedBefore = movement.reservedBefore;
    const reservedAfter = movement.reservedAfter;
    if (
      !movement.id ||
      !movement.productId ||
      movement.warehouseId !== warehouseId ||
      !movement.movementType ||
      !MOVEMENT_TYPES.has(movement.movementType) ||
      !Number.isSafeInteger(quantityDelta) ||
      !Number.isSafeInteger(balanceBefore) ||
      !Number.isSafeInteger(balanceAfter) ||
      !Number.isSafeInteger(reservationDelta) ||
      !Number.isSafeInteger(reservedBefore) ||
      !Number.isSafeInteger(reservedAfter) ||
      balanceBefore! < 0 ||
      balanceAfter! < 0 ||
      reservedBefore! < 0 ||
      reservedAfter! < 0 ||
      balanceAfter !== balanceBefore! + quantityDelta! ||
      reservedAfter !== reservedBefore! + reservationDelta! ||
      (quantityDelta === 0 && reservationDelta === 0) ||
      !movement.occurredAt ||
      !Number.isFinite(Date.parse(movement.occurredAt)) ||
      !movement.responsibleUser?.trim()
    ) {
      throw new Error('Stock movement is incomplete or inconsistent.');
    }
    return movement.id;
  }
}
