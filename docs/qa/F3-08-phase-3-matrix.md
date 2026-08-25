# F3-08 — Matriz transversal de cierre de la Fase 3

**Fecha de auditoría:** 23 de agosto de 2026  
**Alcance:** `F3-01`–`F3-08`, sin adelantar dominios de las fases 4–7.

## Evidencia auditada de F3-01 a F3-07

| Entrega | Capacidad                                  | Evidencia principal                                                                        | Resultado de auditoría                                                      |
| ------- | ------------------------------------------ | ------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| `F3-01` | catálogo lazy de productos, alta y edición | `product-form.spec.ts`, `products-list.spec.ts`, `products.e2e.spec.ts`                    | filtros/paginación remotos, estados, roles y mutaciones cubiertos           |
| `F3-02` | catálogo lazy de almacenes                 | `warehouse-form.spec.ts`, `warehouses-list.spec.ts`, `warehouses.e2e.spec.ts`              | sólo `page`/`size`, `404`, conflictos y desactivación cubiertos             |
| `F3-03` | saldos `MAIN` y por almacén                | `inventory-balances.spec.ts`, `inventory-balances.e2e.spec.ts`                             | composición sin N+1, cero/null, aislamiento y tres roles cubiertos          |
| `F3-04` | mínimo y activación por almacén            | `warehouse-inventory-settings.spec.ts`, `inventory-settings.e2e.spec.ts`                   | estado global/local, `204` + recarga, `409` y permisos cubiertos            |
| `F3-05` | ajuste manual                              | `inventory-adjustment.spec.ts`, `inventory-adjustments.e2e.spec.ts`                        | delta firmado, confirmación, fallos sin replay y reconciliación cubiertos   |
| `F3-06` | alertas y Kardex                           | `inventory-alerts.spec.ts`, `inventory-kardex.spec.ts`, `inventory-operations.e2e.spec.ts` | filtros remotos, historial, fechas, roles y obsolescencia cubiertos         |
| `F3-07` | baja lógica protegida                      | `products-list.spec.ts`, `products.e2e.spec.ts`                                            | suspensión separada, tres `409`, `204`, trazabilidad y doble clic cubiertos |

No se encontraron `skip`, `xit`, `xdescribe`, `test.skip`, `describe.skip` ni `it.skip` en las pruebas de FrontEnd o backend.

## Matriz de roles

| Capacidad de Fase 3                      | `ADMIN` | `INVENTORY_MANAGER` | `SALES` | Evidencia automatizada                                                      |
| ---------------------------------------- | :-----: | :-----------------: | :-----: | --------------------------------------------------------------------------- |
| Consultar productos                      |   Sí    |         Sí          |   Sí    | `products.e2e.spec.ts`, política central y guards                           |
| Crear, editar o dar de baja productos    |   Sí    |         Sí          |   No    | formularios/listado de productos, E2E de administrador, gestor y ventas     |
| Consultar almacenes                      |   Sí    |         Sí          |   Sí    | `warehouses.e2e.spec.ts` y guards                                           |
| Crear, editar o desactivar almacenes     |   Sí    |         Sí          |   No    | formularios/listado de almacenes y E2E por rol                              |
| Consultar saldos `MAIN` y por almacén    |   Sí    |         Sí          |   Sí    | tres casos por rol en `inventory-balances.e2e.spec.ts`                      |
| Configurar mínimo/activación por almacén |   Sí    |         Sí          |   No    | tres casos por rol en `inventory-settings.e2e.spec.ts`                      |
| Ajustar inventario                       |   Sí    |         Sí          |   No    | `inventory-adjustments.e2e.spec.ts` y `SecurityIntegrationTest`             |
| Consultar alertas y Kardex               |   Sí    |         Sí          |   No    | guards lazy y denegación previa a red en `inventory-operations.e2e.spec.ts` |

Los controles de la UI usan `INVENTORY_MANAGEMENT_ROLES`; Spring Security vuelve a autorizar cada petición y conserva `anyRequest().denyAll()`.

## Aislamiento, concurrencia y respuestas obsoletas

| Riesgo                                      | Evidencia FrontEnd                                     | Evidencia backend/PostgreSQL                                                                                                       | Criterio                                                             |
| ------------------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Mezclar `MAIN` con otro almacén             | adaptador de inventario y `inventory-balances.spec.ts` | `WarehouseInventoryIntegrationTest`                                                                                                | IDs, filas y endpoints deben pertenecer a una sola ubicación         |
| Conservar settings de la ubicación anterior | `warehouse-inventory-settings.spec.ts`                 | `WarehouseInventoryIntegrationTest`                                                                                                | `switchMap` cancela y la respuesta se valida por almacén             |
| Aplicar un ajuste al almacén equivocado     | componente y E2E de ajustes                            | `InventoryAdjustmentConcurrencyIntegrationTest`                                                                                    | la ruta y la respuesta deben coincidir con la ubicación seleccionada |
| Alertas/Kardex obsoletos                    | `inventory-alerts.spec.ts`, `inventory-kardex.spec.ts` | `StockMovementIntegrationTest`                                                                                                     | sólo la última combinación ubicación/filtros puede renderizarse      |
| Página de catálogo obsoleta                 | listados de productos y almacenes                      | orden estable probado en integración                                                                                               | una consulta anterior queda desuscrita al cambiar URL                |
| Refresh simultáneo                          | `session.service.spec.ts`                              | `SecuritySessionIntegrationTest`                                                                                                   | varias respuestas `401` comparten un solo refresh                    |
| Reservas/operaciones concurrentes           | no se simula garantía transaccional en Angular         | `InventoryReservationIntegrationTest`, `OrderIntegrationTest`, `InventoryTransferIntegrationTest`, `InventoryCountIntegrationTest` | locks, atomicidad e idempotencia se prueban con PostgreSQL real      |

## Doble envío, fallo y reconciliación

| Mutación                   | Prevención de doble envío                               | Reconciliación autorizada                                     | Fallo/reintento                                      |
| -------------------------- | ------------------------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------- |
| Alta y edición de producto | `submitting` y pruebas de dos llamadas consecutivas     | usa el ID de `ProductResponse` para navegar                   | no hay `retry`; errores permanecen en formulario     |
| Baja de producto           | bloqueo desde apertura del diálogo                      | sólo `204`, recarga catálogo y conserva `productId` histórico | `409` conserva fila; no analiza mensaje ni reintenta |
| Alta y edición de almacén  | `submitting`, incluida confirmación al desactivar       | usa el ID de `WarehouseResponse`                              | no hay retry automático                              |
| Desactivación de almacén   | ID bloqueado mientras la petición está pendiente        | refresco desde API después de `204`                           | `409` conserva almacén y soporte                     |
| Setting de almacén         | formulario deshabilitado mientras guarda                | `PUT 204` seguido de `GET` individual                         | `409` conserva valores del formulario                |
| Ajuste manual              | confirmación y una sola petición marcada no idempotente | reemplaza únicamente con `InventoryResponse`                  | red/`401`/`403`/`429` no disparan replay ni retry    |

`session.service.spec.ts` comprueba además que una petición marcada no idempotente no entra en el replay de autenticación.

## Estados, teclado, móvil y accesibilidad

| Área                                        | Evidencia                                                              |
| ------------------------------------------- | ---------------------------------------------------------------------- |
| carga, vacío y error recuperable            | pruebas de cada listado, balances, settings, alertas y Kardex          |
| `401`, `403`, `409`, `429` y correlation ID | unit/component comunes y `api-errors.e2e.spec.ts`                      |
| validación accesible                        | formularios reactivos, `role="alert"`, resúmenes y etiquetas Material  |
| confirmación y foco                         | pruebas del diálogo compartido y flujos E2E de acciones sensibles      |
| teclado                                     | E2E de productos, almacenes, balances, ajustes, alertas/Kardex y shell |
| móvil                                       | viewports `390×844` sin overflow horizontal en los dominios de Fase 3  |
| QA visual manual                            | inspección inicial y repetición posterior aprobadas el 24 de agosto de 2026 en `1440×900` y `390×844`; fechas localizadas, scroll, foco, responsive y consola verificados |

## QA visual manual — 24 de agosto de 2026

Se levantó el servidor Angular en `http://127.0.0.1:4200` y se recorrió la aplicación en el navegador integrado con una API local controlada para datos de presentación. Docker Desktop no estaba activo, por lo que esta sesión visual no sustituye las verificaciones de integración con PostgreSQL ya aprobadas y registradas abajo.

### Cobertura y resultado

| Área | Escritorio `1440×900` | Móvil `390×844` | Resultado |
| --- | --- | --- | --- |
| login y shell autenticado | tarjeta, encabezado, sidebar y breadcrumbs | tarjeta, encabezado compacto, scrim y menú con scroll interno | aprobado |
| productos | listado, filtros, detalle y formulario; fechas `20 ago 2026` | filtros apilados, filas convertidas en tarjetas, formulario y fechas localizadas | aprobado; `F3-08-V01` cerrado |
| almacenes | listado, detalle, acciones y fechas `20 ago 2026` | filas convertidas en tarjetas y fechas localizadas | aprobado; `F3-08-V01` cerrado |
| saldos y ajuste | selector, definiciones, tabla, panel de ajuste y último movimiento localizado | tarjetas, panel de ajuste, confirmación y último movimiento localizado | aprobado; `F3-08-V01` cerrado |
| alertas | filtros y tabla | filtros apilados y tarjetas | aprobado |
| Kardex | ayudas, filtros, tabla y fechas `20 ago 2026` | ayudas, filtros, tarjetas históricas y fechas localizadas | aprobado; `F3-08-V01` cerrado |
| mínimos/activación | tabla y panel de edición | tarjetas y panel de edición | aprobado |
| foco y teclado | foco en `h1` tras navegación, scroll restaurado y acción principal en confirmación | primer enlace enfocado al abrir menú, scroll restaurado y foco atrapado en confirmación | aprobado; `F3-08-V02` cerrado |
| overflow y consola | ancho del documento `1425 <= 1440`; sin errores o warnings | ancho del documento `375 <= 390`; sin errores o warnings | aprobado |

### Hallazgos y estado de corrección

| ID | Prioridad | Evidencia inicial | Corrección y evidencia automatizada |
| --- | --- | --- | --- |
| `F3-08-V01` | media | detalles de producto/almacén, último movimiento y Kardex mostraban `Aug 20, 2026, 6:00:00 AM` dentro de una interfaz en español | cerrado: `app.config.ts` registra `es-MX`; las cuatro superficies se inspeccionaron visualmente en ambos viewports y muestran `20 ago 2026, 6:00:00 a.m.`, sin fechas en inglés |
| `F3-08-V02` | alta | una navegación desde contenido largo podía conservar `scrollY=133.6` y dejar el `h1` detrás del encabezado fijo | cerrado: el router restaura scroll; en escritorio la navegación pasó de `scrollY=1064` a `0` y en móvil de `scrollY=5632.8` a `0`; el foco quedó en el `h1` y sus posiciones superiores `197.9`/`185.9` px superaron el borde inferior del header fijo (`76` px) |

La repetición manual se ejecutó con el navegador integrado y una API local exclusivamente sintética, sin credenciales reales ni persistencia de sesión. En `1440×900` y `390×844` se verificaron detalle de producto, detalle de almacén, último movimiento y Kardex; ninguna superficie mostró `Aug 20, 2026`. Se reprodujo la navegación desde el final de un listado largo en ambos tamaños y se confirmó `scrollY=0`, foco en el título y ausencia de solapamiento con el header fijo. También se revisaron el menú móvil, alertas, mínimos/activación, ancho de documento `1425 <= 1440` y `375 <= 390`, y consola sin errores ni advertencias. Los hallazgos `F3-08-V01` y `F3-08-V02` quedan cerrados.

## Verificación final de cierre

| Comando o revisión              | Resultado                                                                                                                                                                        |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `npm ci`                        | aprobado: 681 paquetes instalados y 0 vulnerabilidades                                                                                                                          |
| prueba de contrato OpenAPI      | aprobada: 1 prueba, 0 fallos/errores/omitidas; PostgreSQL 17.5 y 16 migraciones Flyway                                                                                           |
| `npm run generate:api:check`    | aprobado después de regenerar el contrato: cliente TypeScript sincronizado                                                                                                      |
| `npm run check`                 | aprobado tras las correcciones: formato, lint, 34 archivos/156 unit-component y 54 E2E; desarrollo 1.50 MB inicial y producción 333.22 kB inicial/92.74 kB estimados, sin advertencias de budget |
| `mvn verify` con Testcontainers | aprobado: 181 pruebas, 0 fallos/errores/omitidas con PostgreSQL real                                                                                                             |
| `mvn spotbugs:check`            | aprobado: 0 bugs y 0 errores                                                                                                                                                     |
| QA visual escritorio/móvil      | aprobada en navegador integrado: cuatro superficies localizadas en `1440×900` y `390×844`; scroll a `0`, foco visible, sin overflow y consola limpia |

Las comprobaciones Maven se ejecutaron secuencialmente con heap limitado porque los intentos previos de la sesión agotaron la memoria nativa de un equipo con 7.73 GB de RAM; la repetición controlada completó contrato, `verify` y SpotBugs sin fallos.

La repetición visual confirmó y cerró `F3-08-V01` y `F3-08-V02`. Con las verificaciones automatizadas previamente aprobadas y la compuerta manual satisfecha, `F3-08` y la Fase 3 quedan completadas.
