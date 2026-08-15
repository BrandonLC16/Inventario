# Inventario API

API REST para administrar productos, existencias, clientes, pedidos y usuarios. Está construida como un monolito modular con Java, Spring Boot y PostgreSQL, y protege sus operaciones mediante JWT y autorización por roles.

## Estado actual

El proyecto implementa:

- CRUD paginado de productos con filtros, SKU único, stock mínimo y borrado lógico.
- Consulta y ajustes atómicos de inventario, sin permitir existencias negativas.
- Bloqueo pesimista durante las modificaciones de stock.
- Consulta operativa paginada del kardex, con filtros y trazabilidad histórica.
- Alertas paginadas de stock bajo o agotado y referencias explícitas de recepción.
- Módulo de clientes con unicidad condicional, búsqueda y desactivación lógica.
- Pedidos paginados con cliente, folio, precios históricos, subtotales, total y auditoría.
- Reservas transaccionales por pedido y producto, sin sobreventa.
- Cálculo de existencia física, reservada y disponible.
- Edición o eliminación de pedidos reservados con liberación atómica.
- Confirmación transaccional: consume la reserva y descuenta físicamente una sola vez.
- Cancelación transaccional: restaura existencias exactamente una vez.
- Órdenes de compra con emisión, recepciones parciales, costos históricos e idempotencia por referencia externa.
- Recepción transaccional de compras con incremento de existencia y movimiento `PURCHASE_RECEIVED`.
- Transferencias entre almacenes con despacho, tránsito consultable, recepción e idempotencia.
- Conteos físicos completos o selectivos con captura no bloqueante, publicación idempotente y ajustes trazables.
- Autenticación por nombre de usuario o correo electrónico.
- Access tokens JWT firmados con RS256.
- Refresh tokens opacos, almacenados como hash, con rotación, revocación por familia y detección de reutilización.
- Roles `ADMIN`, `INVENTORY_MANAGER` y `SALES`.
- Cambio propio y restablecimiento administrativo de contraseñas.
- Revocación inmediata de access tokens y de todas las familias refresh al bloquear, cambiar roles o cambiar contraseña.
- Listados paginados y filtrables de productos, pedidos y usuarios.
- Migraciones de base de datos con Flyway.
- Pruebas unitarias y de integración con PostgreSQL mediante Testcontainers.


## Tecnologías

- Java 25 LTS
- Spring Boot 4.0.7
- Spring Web MVC, Spring Data JPA y Spring Security
- OAuth2 Resource Server y Nimbus JOSE/JWT
- PostgreSQL 17.5 Alpine
- Flyway
- Springdoc OpenAPI 3.0.2
- Maven Wrapper
- JUnit, Mockito y Testcontainers
- SpotBugs y Trivy en integración continua
- Docker Compose

## Requisitos

- JDK 25 LTS disponible en `PATH`.
- Docker con el plugin de Compose.
- OpenSSL u otra herramienta capaz de generar claves RSA en formato PKCS#8 y X.509.

No es necesario instalar Maven: el repositorio incluye `mvnw` y `mvnw.cmd`.

```text
java -version
docker version
docker compose version
```

## Inicio local

### 1. Clonar el repositorio

```bash
git clone https://github.com/BrandonLC16/Inventario.git
cd Inventario
```

### 2. Iniciar PostgreSQL

```bash
docker compose up -d
docker compose ps
```

`compose.yaml` es exclusivamente para desarrollo individual: publica PostgreSQL sólo en `127.0.0.1:5432` y usa `inventory` como nombre de base, usuario y contraseña. No debe desplegarse en ambientes compartidos, staging ni producción.

### 3. Generar las claves JWT

La aplicación no incluye claves ni secretos predeterminados. Genera las claves fuera del repositorio:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out inventory-private.pem
openssl pkey -in inventory-private.pem -pubout -out inventory-public.pem
```

La clave privada debe estar en formato PKCS#8 y la pública en formato X.509. No agregues estos archivos a Git.

### 4. Crear el primer administrador

En una base de datos sin usuarios, habilita el bootstrap una sola vez. En PowerShell:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_USERNAME = "admin-local"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@example.test"
$env:BOOTSTRAP_ADMIN_PASSWORD = "<mínimo-12-caracteres>"
$env:JWT_PUBLIC_KEY_LOCATION = "file:C:/ruta-segura/inventory-public.pem"
$env:JWT_PRIVATE_KEY_LOCATION = "file:C:/ruta-segura/inventory-private.pem"
./mvnw.cmd spring-boot:run
```

En macOS o Linux usa `export`, rutas con el formato `file:/ruta/...` y `./mvnw`.

El inicializador solo crea el usuario cuando la tabla `app_users` está vacía. Después del primer arranque, deshabilita `BOOTSTRAP_ADMIN_ENABLED` y elimina juntas las tres variables de credenciales del bootstrap. Una configuración parcial se considera un error y evita que la aplicación inicie.

La API queda disponible en `http://localhost:8080`. Flyway aplica las migraciones pendientes al arrancar y Hibernate valida el esquema resultante.

## Autenticación

| Método | Ruta | Acceso | Resultado |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Público | Emite un access token y un refresh token |
| `POST` | `/api/v1/auth/refresh` | Público, requiere refresh token | Rota el refresh token y emite un par nuevo |
| `POST` | `/api/v1/auth/logout` | Público, requiere refresh token | Revoca la familia del refresh token y responde `204` |
| `GET` | `/api/v1/auth/me` | Autenticado | Devuelve la identidad y los roles actuales |
| `PUT` | `/api/v1/auth/password` | Autenticado | Cambia la contraseña propia y responde `204` |

`identifier` acepta el nombre de usuario o el correo. La respuesta incluye `tokenType`, `accessToken`, `accessTokenExpiresAt`, `refreshToken` y `refreshTokenExpiresAt`.

Envía el access token como `Authorization: Bearer <ACCESS_TOKEN>`. Para renovar o cerrar la sesión, envía `{"refreshToken":"<REFRESH_TOKEN>"}` como JSON a la ruta correspondiente. Las respuestas de login y renovación incluyen `Cache-Control: no-store` y `Pragma: no-cache`.

El servidor persiste únicamente el hash SHA-256 del refresh token aleatorio, nunca su valor, y lo rota en cada renovación bajo bloqueo transaccional. El cliente debe mantener el access token en memoria. Una aplicación nativa debe guardar el refresh token en Keychain/Keystore; una aplicación web debe delegarlo a un BFF que use una cookie `HttpOnly`, `Secure` y `SameSite`, porque esta API recibe el refresh como JSON y no debe guardarse en `localStorage` ni `sessionStorage`. El cliente reemplaza el refresh anterior sólo después de recibir el nuevo par, serializa renovaciones concurrentes y, ante un `401`, elimina la sesión local y vuelve a autenticar.

Los fallos de login, los usuarios inexistentes, deshabilitados o bloqueados y los refresh tokens inválidos al renovar usan la misma respuesta genérica `401`. Un refresh token rotado o revocado no puede reutilizarse. El logout revoca toda su familia y es idempotente: un token desconocido también recibe `204`.

`login`, `refresh` y `logout` limitan intentos por cliente y por huella SHA-256 de la credencial, sin conservar identificadores ni tokens en claro. La huella de credencial es global y no incluye la IP, por lo que cambiar de origen no reinicia su contador. El valor predeterminado permite 5 intentos por credencial y 100 por cliente en una ventana de un minuto; al excederlo responde `429`, código `RATE_LIMIT_EXCEEDED` y cabecera `Retry-After`. Los contadores y ventanas se actualizan atómicamente en PostgreSQL y se comparten entre réplicas; las ventanas vencidas dejan de contar inmediatamente y sus filas se limpian periódicamente.

Sin proxies confiables configurados se usa exclusivamente la dirección TCP del peer y se ignora `X-Forwarded-For`. Detrás de un gateway, `AUTH_TRUSTED_PROXIES` debe contener sus IP o redes CIDR; sólo entonces se recorre la cadena de derecha a izquierda hasta encontrar el cliente no confiable más cercano. El gateway debe anexar o reemplazar correctamente esa cabecera y mantener su propio límite perimetral como defensa adicional.

Cada access token incluye una versión de seguridad que se contrasta con PostgreSQL en todas las peticiones autenticadas. El logout revoca su familia refresh e invalida inmediatamente los access tokens anteriores; repetirlo no invalida tokens nuevos. Bloquear, deshabilitar, cambiar roles o cambiar/restablecer la contraseña incrementa esa versión y revoca todas las familias refresh activas del usuario dentro de la misma transacción. La revocación administrativa de sesiones aplica la misma regla sin cambiar la contraseña.

Para cambiar la contraseña propia envía `{"currentPassword":"<ACTUAL>","newPassword":"<NUEVA>"}`. La nueva contraseña debe tener entre 12 y 128 caracteres y ser diferente de la actual.

## Roles y permisos

| Operación | `ADMIN` | `INVENTORY_MANAGER` | `SALES` |
|---|:---:|:---:|:---:|
| Consultar productos e inventario | Sí | Sí | Sí |
| Crear, modificar o eliminar productos | Sí | Sí | No |
| Ajustar inventario | Sí | Sí | No |
| Consultar el kardex | Sí | Sí | No |
| Consultar alertas de stock bajo | Sí | Sí | No |
| Crear, actualizar o desactivar almacenes y cambiar su configuración | Sí | Sí | No |
| Crear, consultar, actualizar o desactivar clientes | Sí | No | Sí |
| Crear, consultar, reservar, liberar, confirmar o cancelar pedidos | Sí | No | Sí |
| Crear, emitir, recibir o cancelar órdenes de compra | Sí | Sí | No |
| Crear, despachar, recibir o cancelar transferencias | Sí | Sí | No |
| Crear, capturar, publicar o cancelar conteos físicos | Sí | Sí | No |
| Administrar usuarios y roles | Sí | No | No |
| Acceder a OpenAPI/Swagger habilitado | Sí | No | No |

La autorización HTTP es cerrada por defecto: toda ruta debe incorporarse explícitamente a esta matriz. Una ruta nueva sin regla recibe `403` incluso con un token válido.

## Productos e inventario

Todos estos endpoints requieren un access token.

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/products` | Autenticado | Lista paginada; filtros `sku`, `name` y `active` |
| `GET` | `/api/v1/products/{id}` | Autenticado | Consulta un producto no eliminado |
| `POST` | `/api/v1/products` | `ADMIN`, `INVENTORY_MANAGER` | Crea un producto |
| `PUT` | `/api/v1/products/{id}` | `ADMIN`, `INVENTORY_MANAGER` | Reemplaza los campos editables |
| `DELETE` | `/api/v1/products/{id}` | `ADMIN`, `INVENTORY_MANAGER` | Elimina lógicamente sólo si no hay existencias, reservas ni documentos pendientes; responde `204` |
| `GET` | `/api/v1/inventory` | Autenticado | Lista paginada de existencias de todos los productos no eliminados |
| `GET` | `/api/v1/inventory/{productId}` | Autenticado | Consulta las existencias |
| `GET` | `/api/v1/inventory/movements` | `ADMIN`, `INVENTORY_MANAGER` | Lista paginada de movimientos de todos los productos |
| `GET` | `/api/v1/inventory/{productId}/movements` | `ADMIN`, `INVENTORY_MANAGER` | Consulta el kardex de un producto |
| `GET` | `/api/v1/inventory/low-stock` | `ADMIN`, `INVENTORY_MANAGER` | Lista alertas de stock bajo o agotado |
| `PATCH` | `/api/v1/inventory/{productId}/adjustments` | `ADMIN`, `INVENTORY_MANAGER` | Suma o resta existencias |

### Almacenes e inventario por almacén

V10 crea el almacén determinista `MAIN` (`00000000-0000-0000-0000-000000000001`) y le asigna las existencias, reservas, movimientos, pedidos y mínimos preexistentes. Las rutas históricas `/api/v1/inventory/**` permanecen como alias compatibles hacia `MAIN`.

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/warehouses` | Autenticado | Lista almacenes |
| `POST` | `/api/v1/warehouses` | `ADMIN`, `INVENTORY_MANAGER` | Crea un almacén |
| `GET` | `/api/v1/warehouses/{id}` | Autenticado | Consulta un almacén |
| `PUT` | `/api/v1/warehouses/{id}` | `ADMIN`, `INVENTORY_MANAGER` | Actualiza un almacén |
| `DELETE` | `/api/v1/warehouses/{id}` | `ADMIN`, `INVENTORY_MANAGER` | Desactiva un almacén vacío y sin documentos abiertos |
| `GET` | `/api/v1/warehouses/{id}/inventory` | Autenticado | Lista balances del almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/{productId}` | Autenticado | Consulta un balance físico, reservado y disponible |
| `PATCH` | `/api/v1/warehouses/{id}/inventory/{productId}/adjustments` | `ADMIN`, `INVENTORY_MANAGER` | Ajusta existencias del almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/movements` | `ADMIN`, `INVENTORY_MANAGER` | Consulta el kardex del almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/low-stock` | `ADMIN`, `INVENTORY_MANAGER` | Consulta alertas configuradas por almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/settings` | Autenticado | Lista la configuración de productos, incluidos los inactivos |
| `GET` | `/api/v1/warehouses/{id}/inventory/{productId}/settings` | Autenticado | Consulta `minimumStock` y activación del producto en el almacén |
| `PUT` | `/api/v1/warehouses/{id}/inventory/{productId}/settings` | `ADMIN`, `INVENTORY_MANAGER` | Configura `minimumStock` y activación por almacén |

Cada balance, movimiento y alerta incluye `warehouseId`. Un almacén inactivo no admite ajustes ni reservas. Un producto no puede desactivarse en un almacén mientras conserve existencias físicas o reservas; primero deben retirarse o liberarse. La disponibilidad se calcula siempre como `quantity - reservedQuantity` y ninguna operación permite existencia o disponibilidad negativa.
Producto de ejemplo:

```json
{"sku":"KBD-001","name":"Teclado","description":null,"price":1299.90,"active":true,"minimumStock":5}
```

`minimumStock` en la creación sólo inicializa la configuración de `MAIN`.
`PUT /api/v1/products/{id}` actualiza exclusivamente el catálogo y nunca modifica
el mínimo ni la activación de ningún almacén; esos valores se cambian mediante el
endpoint de `settings` del almacén.

El SKU se recorta y guarda en mayúsculas. Su unicidad no distingue mayúsculas y el SKU de un producto eliminado continúa reservado.

### Ciclo de vida de un producto

`active=false` representa una suspensión reversible. El producto continúa visible en el catálogo, los balances y la configuración de almacenes para que sus existencias físicas no queden ocultas, pero no admite nuevas ventas, compras, transferencias, ajustes ni conteos físicos. Las operaciones compensatorias de ventas ya confirmadas —como cancelar y restaurar existencias— sí pueden terminar para conservar la integridad contable.

`deleted=true` representa una baja lógica terminal: también fuerza `active=false` y oculta el producto de las vistas operativas, aunque conserva el registro y su kardex histórico. La baja se rechaza con `409` mientras exista stock físico, una reserva o un documento pendiente que todavía pueda mover inventario: pedidos `PENDING`, `RESERVED` o `CONFIRMED`; compras `DRAFT`, `ISSUED` o `PARTIALLY_RECEIVED`; transferencias `DRAFT` o `IN_TRANSIT`; y conteos `DRAFT`, `OPEN` o `SUBMITTED`. Primero debe cerrarse el documento y llevarse el saldo a cero.

Para ajustar inventario envía `{"quantityDelta":10,"reference":"PURCHASE-RECEIPT-42"}`. El valor debe ser distinto de cero: uno positivo registra una entrada y uno negativo una salida. `reference` es opcional, admite hasta 128 caracteres y permite identificar una compra, recepción u otra operación. Una salida responde `400` si consume unidades reservadas o deja el saldo físico negativo.

`GET /api/v1/inventory` y `GET /api/v1/inventory/{productId}` devuelven `quantity` como existencia física, `reservedQuantity`, `availableQuantity` y `updatedAt`. El listado general incluye también productos sin fila de inventario con saldos cero. Siempre se cumple `availableQuantity = quantity - reservedQuantity`; en la consulta individual de un producto todavía sin inventario, los tres saldos son cero y `updatedAt` es `null`.

Cada ajuste exitoso genera un movimiento con saldo anterior y posterior, tipo, referencia y el UUID del usuario autenticado en `responsible_user`. La primera entrada usa `INITIAL_STOCK`; las siguientes, `MANUAL_IN` o `MANUAL_OUT`.

`GET /api/v1/products` usa `page=0`, `size=20` y admite tamaños entre 1 y 100. Los filtros de SKU y nombre son coincidencias parciales sin distinguir mayúsculas. Todos los listados paginados devuelven `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y `last`.

### Alertas y reposición

`GET /api/v1/inventory/low-stock` incluye productos activos no eliminados cuya existencia disponible es menor o igual a `minimumStock`, aunque todavía no tengan fila de inventario. Admite `page`, `size`, búsqueda parcial por SKU o nombre mediante `search`, y `outOfStockOnly=true`. Cada resultado contiene existencia física, reservada y disponible, mínimo, cantidad sugerida de reposición y una alerta `LOW_STOCK` u `OUT_OF_STOCK`.

### Consulta del kardex

`GET /api/v1/inventory/movements` y `GET /api/v1/inventory/{productId}/movements` devuelven los movimientos en orden descendente por `occurredAt` y usan `id` como desempate para mantener estable la paginación. El historial se puede consultar incluso si el producto fue eliminado lógicamente.

| Parámetro | Predeterminado | Descripción |
|---|---|---|
| `page` | `0` | Índice de página, desde cero |
| `size` | `20` | Elementos por página, entre 1 y 100 |
| `type` | Sin filtro | `INITIAL_STOCK`, `MANUAL_IN`, `MANUAL_OUT`, `ORDER_RESERVED`, `ORDER_RESERVATION_RELEASED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `PURCHASE_RECEIVED`, `TRANSFER_OUT`, `TRANSFER_IN` o `PHYSICAL_COUNT_ADJUSTMENT` |
| `from` | Sin filtro | Fecha inicial inclusiva en ISO-8601 |
| `to` | Sin filtro | Fecha final inclusiva en ISO-8601 |
| `reference` | Sin filtro | Coincidencia exacta con la referencia de negocio |
| `productId` | Sin filtro | Sólo en el listado general; limita el resultado a un producto |

Los filtros son opcionales y combinables. `from` no puede ser posterior a `to`. Cada elemento contiene `movementType`, el efecto físico mediante `quantityDelta`, `balanceBefore` y `balanceAfter`, y el efecto comprometido mediante `reservationDelta`, `reservedBefore` y `reservedAfter`. También incluye `businessReference`, `occurredAt`, `responsibleUser` y los identificadores.

La respuesta contiene `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y `last`.

## Clientes

Estas rutas requieren `ADMIN` o `SALES`.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/customers` | Crea un cliente |
| `GET` | `/api/v1/customers` | Busca clientes con paginación |
| `GET` | `/api/v1/customers/{id}` | Consulta un cliente |
| `PUT` | `/api/v1/customers/{id}` | Reemplaza sus datos editables |
| `DELETE` | `/api/v1/customers/{id}` | Lo desactiva lógicamente; responde `204` |

Ejemplo:

```json
{"name":"Cliente Ejemplo","fiscalIdentifier":"MX-RFC-100","email":"cliente@example.test","active":true}
```

El identificador fiscal y el correo son opcionales; cuando existen, cada uno debe ser único. El identificador fiscal se guarda en mayúsculas y el correo en minúsculas. El listado usa `page`, `size`, `search` —sobre nombre, identificador fiscal o correo— y el filtro opcional `active`. Los pedidos nuevos sólo pueden asociarse a clientes activos.

## Pedidos

Estas rutas requieren `ADMIN` o `SALES`.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/orders` | Crea un pedido `PENDING` |
| `GET` | `/api/v1/orders` | Lista pedidos paginados por fecha descendente |
| `GET` | `/api/v1/orders/{id}` | Consulta un pedido y sus artículos |
| `PUT` | `/api/v1/orders/{id}/items` | Reemplaza artículos; libera antes si estaba reservado |
| `DELETE` | `/api/v1/orders/{id}` | Elimina un pendiente o reservado; libera si aplica |
| `POST` | `/api/v1/orders/{id}/reserve` | Reserva atómicamente todos los artículos |
| `POST` | `/api/v1/orders/{id}/release` | Libera la reserva y vuelve a `PENDING` |
| `POST` | `/api/v1/orders/{id}/confirm` | Convierte la reserva en salida física |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancela un confirmado y restaura existencias |

Ejemplo de creación:

```json
{"customerId":"<CUSTOMER_ID>","items":[{"productId":"<PRODUCT_ID>","quantity":2}]}
```

Un pedido admite de 1 a 100 productos distintos y cada cantidad debe estar entre 1 y 1,000,000. Este máximo garantiza que subtotales y total siempre caben en los campos monetarios aun usando los precios máximos admitidos. `customerId` es opcional. Los productos repetidos se rechazan con `400`.

Al crear el pedido se captura el precio unitario vigente y se calculan el subtotal de cada artículo y el total. Esos importes no cambian aunque después se modifique el producto. La moneda actual es fija, `MXN`; el diseño persiste el código para permitir una futura estrategia multimoneda. También se genera un folio `ORD-##########` y se registra el UUID de quien creó, confirmó o canceló, junto con las fechas correspondientes.

`GET /api/v1/orders` admite `page`, `size`, `status`, `customerId`, coincidencia parcial de `folio`, y rango inclusivo `from`/`to` en ISO-8601 sobre `createdAt`. Todos los filtros son combinables.

El ciclo es `PENDING → RESERVED → CONFIRMED → CANCELLED`. Un `RESERVED` puede volver a `PENDING` mediante `release`. Confirmar exige reserva previa; cancelar exige `CONFIRMED`. Reservar, liberar, confirmar y cancelar son idempotentes cuando se repiten sobre el estado que ya representan.

La reserva crea una fila por pedido y producto, conserva intacta la existencia física y reduce la disponible. El servicio bloquea el pedido y después cada inventario en orden estable por UUID. Si algún artículo carece de disponibilidad, toda la reserva, sus eventos y el cambio de estado se revierten. Dos pedidos simultáneos no pueden reservar las mismas unidades ni provocar sobreventa.

Editar un `RESERVED` libera todos sus artículos y lo devuelve a `PENDING` antes de reemplazarlos y recapturar precios; cualquier fallo posterior revierte también la liberación. Eliminar un reservado aplica la misma liberación transaccional.

La confirmación elimina cada reserva y registra en un único `ORDER_CONFIRMED` tanto el descenso físico como el descenso reservado, por lo que no descuenta dos veces. La cancelación restaura existencia física una sola vez mediante `ORDER_CANCELLED`. `ORDER_RESERVED` y `ORDER_RESERVATION_RELEASED` auditan reservas/liberaciones con referencia al pedido y UUID del actor. La cancelación sigue funcionando si el producto fue desactivado; el producto no puede eliminarse mientras exista un pedido confirmado pendiente de esa compensación.

## Compras y recepciones

Estas rutas requieren `ADMIN` o `INVENTORY_MANAGER`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/purchase-orders` | Lista órdenes paginadas y filtrables |
| `POST` | `/api/v1/purchase-orders` | Crea una orden `DRAFT` |
| `GET` | `/api/v1/purchase-orders/{id}` | Consulta la orden y sus cantidades pendientes |
| `PUT` | `/api/v1/purchase-orders/{id}/items` | Reemplaza artículos o destino de un borrador |
| `POST` | `/api/v1/purchase-orders/{id}/issue` | Emite una orden de compra |
| `POST` | `/api/v1/purchase-orders/{id}/receipts` | Registra una recepción parcial o total |
| `POST` | `/api/v1/purchase-orders/{id}/cancel` | Cancela una orden sin recepciones |
| `GET` | `/api/v1/purchase-orders/{id}/receipts` | Lista el historial de recepciones |

Ejemplo de creación:

```json
{
  "supplierId": "<SUPPLIER_ID>",
  "destinationWarehouseId": "<WAREHOUSE_ID>",
  "currency": "MXN",
  "supplierReference": "PO-PROVEEDOR-42",
  "items": [{
    "productId": "<PRODUCT_ID>",
    "supplierSku": "SKU-PROVEEDOR",
    "orderedQuantity": 10,
    "unitCost": 125.5000
  }]
}
```

La orden sigue `DRAFT → ISSUED → PARTIALLY_RECEIVED → RECEIVED`; un borrador o una orden emitida sin recepciones puede pasar a `CANCELLED`. Solo `DRAFT` permite cambiar artículos o almacén. Admite hasta 100 artículos y cada cantidad debe estar entre 1 y 10,000 para mantener subtotales y total dentro de `NUMERIC(20,4)` con cualquier costo válido. Cada artículo conserva el costo y SKU capturados, y cada recepción conserva su propio costo sin modificar el precio de venta del producto.

Para recibir se envía una referencia externa obligatoria y artículos identificados por `purchaseOrderItemId`:

```json
{
  "externalReference": "REMISION-9001",
  "updateSupplierProductLastCost": true,
  "items": [{
    "purchaseOrderItemId": "<PURCHASE_ORDER_ITEM_ID>",
    "quantity": 4,
    "unitCost": 127.2500
  }]
}
```

La primera recepción responde `201`; repetir la misma referencia, artículos, cantidades, costos y valor de `updateSupplierProductLastCost` devuelve la recepción existente con `200`, sin duplicar existencia, movimientos ni actualizaciones de costo. Cambiar cualquiera de esos datos responde `409`. Las recepciones creadas antes de la migración V16 conservan la bandera como desconocida y sus reintentos se rechazan de forma segura. No se puede recibir más de lo pendiente ni cancelar una orden que ya tenga recepciones.

La recepción bloquea la orden, sus artículos y los inventarios en orden de UUID de producto. En una sola transacción crea la recepción, incrementa el almacén destino, registra `PURCHASE_RECEIVED`, actualiza cantidades y estado y, cuando se solicita, actualiza `lastUnitCost` de una asociación proveedor-producto existente. Un fallo revierte todos esos efectos.

`GET /api/v1/purchase-orders` admite `page`, `size`, `status`, `supplierId`, `destinationWarehouseId`, coincidencia parcial de `folio` y rango inclusivo `from`/`to` sobre `createdAt`.

## Transferencias entre almacenes

Estas rutas requieren `ADMIN` o `INVENTORY_MANAGER`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/inventory-transfers` | Lista transferencias paginadas y filtrables |
| `POST` | `/api/v1/inventory-transfers` | Crea una transferencia `DRAFT` |
| `GET` | `/api/v1/inventory-transfers/{id}` | Consulta el documento y sus unidades en tránsito |
| `PUT` | `/api/v1/inventory-transfers/{id}/items` | Reemplaza artículos o almacenes de un borrador |
| `POST` | `/api/v1/inventory-transfers/{id}/dispatch` | Despacha existencias del origen |
| `POST` | `/api/v1/inventory-transfers/{id}/receive` | Recibe existencias en el destino |
| `POST` | `/api/v1/inventory-transfers/{id}/cancel` | Cancela un borrador |

Ejemplo de creación:

```json
{
  "sourceWarehouseId": "<SOURCE_WAREHOUSE_ID>",
  "destinationWarehouseId": "<DESTINATION_WAREHOUSE_ID>",
  "items": [{"productId": "<PRODUCT_ID>", "quantity": 5}]
}
```

El ciclo es `DRAFT → IN_TRANSIT → RECEIVED`; únicamente `DRAFT` se puede editar o cancelar. Origen y destino deben ser distintos y permanecer activos mientras el documento esté abierto. Una transferencia despachada no se cancela: debe recibirse o compensarse mediante otra transferencia.

El despacho bloquea el documento, sus artículos, ambos almacenes y los inventarios en orden estable por UUID de producto. Valida la existencia disponible (`quantity - reservedQuantity`), conserva intactas las reservas de venta, descuenta físicamente del origen y registra `TRANSFER_OUT`. La recepción suma exactamente las mismas cantidades en el destino y registra `TRANSFER_IN`. Ambos movimientos usan el UUID de la transferencia como `businessReference`.

Despacho y recepción son idempotentes. Repetir cualquiera de los comandos después de su aplicación devuelve el estado actual sin duplicar saldos ni movimientos. Cualquier fallo multiartículo revierte todos los saldos, movimientos y el cambio de estado.

Cada artículo expone `quantity` e `inTransitQuantity`; esta última solo es distinta de cero en `IN_TRANSIT`. La respuesta también incluye los totales `totalQuantity` e `inTransitQuantity`, por lo que la conservación se consulta como existencia en origen más tránsito más existencia en destino.

`GET /api/v1/inventory-transfers` admite `page`, `size`, `status`, `sourceWarehouseId`, `destinationWarehouseId`, coincidencia parcial de `folio` y rango inclusivo `from`/`to` sobre `createdAt`.

## Conteos físicos

Estas rutas requieren `ADMIN` o `INVENTORY_MANAGER`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/inventory-counts` | Lista conteos paginados y filtrables |
| `POST` | `/api/v1/inventory-counts` | Crea un conteo `DRAFT` completo o selectivo |
| `GET` | `/api/v1/inventory-counts/{id}` | Consulta el documento, snapshots y variaciones |
| `POST` | `/api/v1/inventory-counts/{id}/open` | Abre el conteo y captura saldos iniciales |
| `PUT` | `/api/v1/inventory-counts/{id}/lines/{productId}` | Captura o corrige la cantidad física |
| `POST` | `/api/v1/inventory-counts/{id}/submit` | Envía un conteo totalmente capturado |
| `POST` | `/api/v1/inventory-counts/{id}/post` | Publica sus variaciones de forma idempotente |
| `POST` | `/api/v1/inventory-counts/{id}/cancel` | Cancela un conteo que aún no fue publicado |

Un conteo selectivo usa `scope: "SELECTED"` y una lista de productos; uno completo usa `scope: "FULL"` y toma todos los productos registrados en el almacén:

```json
{
  "warehouseId": "<WAREHOUSE_ID>",
  "scope": "SELECTED",
  "productIds": ["<PRODUCT_ID>"]
}
```

Todo conteo está limitado a 1,000 productos. Para `FULL` la consulta obtiene como máximo 1,001 identificadores: si detecta el exceso responde `409` antes de crear líneas o iniciar los bloqueos; el inventario debe dividirse en conteos `SELECTED` de hasta 1,000 productos. La apertura, envío y publicación también rechazan documentos históricos que excedan ese límite.

El ciclo es `DRAFT → OPEN → SUBMITTED → POSTED`; cualquier estado no publicado puede pasar a `CANCELLED`. No puede existir más de un conteo activo para el mismo almacén y producto, incluso ante creaciones concurrentes. `OPEN` es el único estado que acepta cantidades y todas deben ser no negativas; `SUBMITTED` exige que cada línea haya sido capturada.

La apertura toma snapshots consistentes bajo bloqueos breves de los inventarios, pero no detiene el almacén después de confirmar la transacción. Al capturar una línea, el servicio bloquea ese inventario y suma a su snapshot los movimientos físicos posteriores usando el kardex; así obtiene el saldo esperado en `countedAt` y calcula `variance = countedQuantity - expectedQuantity`. Una corrección posterior repite el cálculo desde el snapshot anterior, sin perder entradas o salidas concurrentes.

Publicar bloquea el documento, sus líneas y los inventarios en orden de UUID, y aplica cada variación sobre el saldo actual. Si cualquier saldo resultante queda por debajo de sus reservas de venta, toda la publicación se rechaza y revierte. Solo las variaciones distintas de cero generan `PHYSICAL_COUNT_ADJUSTMENT`, con el UUID del conteo como `businessReference`; repetir una publicación exitosa no duplica saldos ni movimientos.

`GET /api/v1/inventory-counts` admite `page`, `size`, `status`, `scope`, `warehouseId` y coincidencia parcial de `folio`.

## Administración de usuarios

Todas estas rutas requieren el rol `ADMIN`.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/users` | Crea un usuario |
| `GET` | `/api/v1/users` | Lista usuarios paginados por nombre |
| `GET` | `/api/v1/users/{id}` | Consulta un usuario |
| `PATCH` | `/api/v1/users/{id}/status` | Cambia `enabled` y `locked` |
| `PUT` | `/api/v1/users/{id}/roles` | Reemplaza todos los roles |
| `PUT` | `/api/v1/users/{id}/password` | Restablece la contraseña y responde `204` |
| `POST` | `/api/v1/users/{id}/sessions/revoke` | Revoca todas las sesiones y responde `204` |

Ejemplo de creación:

```json
{"username":"sales.one","email":"sales.one@example.test","password":"<mínimo-12-caracteres>","roles":["SALES"]}
```

El usuario y correo se normalizan a minúsculas y deben ser únicos. Los roles no pueden quedar vacíos. Las respuestas nunca incluyen contraseña ni hash, y el servicio protege al último administrador activo.

El listado admite `page`, `size`, búsqueda parcial por `username` y `email`, además de filtros exactos `role`, `enabled` y `locked`. El restablecimiento recibe `{"newPassword":"<MÍNIMO-12-CARACTERES>"}`. Cambiar estado, roles o contraseña revoca access y refresh tokens anteriores al confirmar la transacción.

## Errores HTTP

Los errores usan los campos `timestamp`, `status`, `error`, `code`, `message`, `path`, `correlationId` y `validationErrors`. `code` es estable y apto para lógica de cliente: `INVALID_REQUEST`, `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, `CONFLICT`, `DATA_INTEGRITY_VIOLATION`, `AUTHENTICATION_FAILED`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` o `INTERNAL_ERROR`.

Toda respuesta incluye `X-Correlation-ID`. Si la petición trae un valor seguro de hasta 128 caracteres, se conserva; de lo contrario se genera un UUID. En un error, la cabecera y `correlationId` contienen el mismo valor. Usa este identificador para soporte y trazabilidad, no mensajes variables ni el texto de `error`.

- `400`: cuerpo inválido, ajuste cero, inventario insuficiente o filtros/paginación inválidos.
- `401`: autenticación ausente o inválida.
- `403`: rol insuficiente.
- `404`: producto, cliente, pedido o usuario inexistente.
- `409`: identidad, correo, identificador fiscal o SKU duplicado; protección del último administrador; cliente inactivo o transición de pedido inválida.

## OpenAPI y Swagger UI

La documentación está deshabilitada por defecto. Define `SWAGGER_ENABLED=true` y reinicia la aplicación para habilitar:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

Ambas rutas siguen protegidas y requieren un access token con rol `ADMIN`.

El contrato se genera de forma reproducible desde los controllers durante `verify` en `target/openapi/inventory-api-v1.json`. La prueba falla si aparece una ruta fuera de `/api/v1` o si faltan los listados globales. Para regenerarlo de forma aislada, con Docker iniciado:

```powershell
./mvnw.cmd "-Dtest=OpenApiContractIntegrationTest" test
```

## Configuración

| Variable | Predeterminado | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/inventory` | URL JDBC |
| `DB_USERNAME` | `inventory` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `inventory` | Contraseña de PostgreSQL |
| `DB_LOCK_TIMEOUT` | `5s` | Espera máxima de PostgreSQL para adquirir un bloqueo |
| `DB_STATEMENT_TIMEOUT` | `30s` | Duración máxima de una sentencia PostgreSQL |
| `DB_TRANSACTION_TIMEOUT` | `30s` | Duración máxima predeterminada de una transacción Spring |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | `5000` | Espera máxima en milisegundos para obtener una conexión del pool |
| `JWT_PUBLIC_KEY_LOCATION` | Requerida | Clave pública X.509 |
| `JWT_PRIVATE_KEY_LOCATION` | Requerida | Clave privada PKCS#8 |
| `JWT_ISSUER` | `inventory-api` | Claim `iss` |
| `JWT_AUDIENCE` | `inventory-clients` | Claim `aud` |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | TTL de access; entre 1 minuto y 1 hora |
| `JWT_REFRESH_TOKEN_TTL` | `14d` | TTL de refresh; entre 1 hora y 90 días |
| `AUTH_RATE_LIMIT_PER_CREDENTIAL` | `5` | Intentos de login, refresh o logout por credencial y ventana |
| `AUTH_RATE_LIMIT_PER_CLIENT` | `100` | Intentos totales de autenticación por cliente y ventana |
| `AUTH_RATE_LIMIT_WINDOW` | `1m` | Ventana entre 1 segundo y 1 hora |
| `AUTH_RATE_LIMIT_MAX_TRACKED_KEYS` | `10000` | Máximo de contadores activos compartidos; al agotarse se rechazan claves nuevas |
| `AUTH_TRUSTED_PROXIES` | Lista vacía | IP o CIDR de proxies confiables, separados por comas, autorizados a aportar `X-Forwarded-For` |
| `CORS_ALLOWED_ORIGINS` | Lista vacía | Orígenes separados por comas |
| `SWAGGER_ENABLED` | `false` | Habilita Swagger para `ADMIN` |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` | Habilita el administrador inicial |
| `BOOTSTRAP_ADMIN_USERNAME` | Sin valor | Usuario inicial |
| `BOOTSTRAP_ADMIN_EMAIL` | Sin valor | Correo inicial |
| `BOOTSTRAP_ADMIN_PASSWORD` | Sin valor | Contraseña inicial, 12 a 128 caracteres |

En producción usa un gestor de secretos. CORS no admite credenciales ni el comodín `*`; sin orígenes configurados rechaza cross-origin. La autenticación directa usa Bearer y JSON, no cookies, por lo que la API es stateless y CSRF está deshabilitado. `X-Correlation-ID` está permitido y expuesto a los clientes CORS.

La política es explícita por ambiente:

- Predeterminado, staging y producción: cierre seguro; define `CORS_ALLOWED_ORIGINS` con los orígenes HTTPS exactos de ese ambiente, separados por comas. No se configura un origen de producción en el repositorio.
- Local: activa `SPRING_PROFILES_ACTIVE=local`; `application-local.yml` admite por defecto `http://localhost:3000`, `http://localhost:4200` (Angular CLI) y `http://localhost:5173`, reemplazables mediante `CORS_ALLOWED_ORIGINS`.
- Pruebas: Testcontainers valida `https://allowed.example` y el origen local de Angular CLI.

## Pruebas

Docker debe estar iniciado. Testcontainers crea PostgreSQL temporal; no es necesario ejecutar antes `docker compose up`.

Windows:

```powershell
./mvnw.cmd clean test
./mvnw.cmd verify
```

macOS o Linux:

```bash
./mvnw clean test
./mvnw verify
```

La suite combina pruebas unitarias y de integración sobre PostgreSQL. Cubre productos, inventario, configuración por almacén, reservas, kardex, clientes, pedidos, compras, recepciones, transferencias, conteos físicos, usuarios, autenticación, límites de intentos, refresh tokens, JWT y autorización HTTP.

Las pruebas PostgreSQL validan entradas simultáneas sin actualizaciones perdidas ni más de un `INITIAL_STOCK`; salidas simultáneas sin saldo negativo; reservas competitivas sin sobreventa; rollback multiartículo; edición, liberación y eliminación reservada; confirmación sin doble descuento; cancelación idempotente; recepciones parciales e idempotentes; transferencias competitivas y contra stock reservado; conservación origen-tránsito-destino; conteos físicos concurrentes, no bloqueantes, idempotentes y compatibles con reservas; kardex físico/reservado consecutivo; upgrade V9→V10 con backfill histórico multi-almacén; precios y costos históricos; filtros; alertas; permisos; y revocación inmediata de access/refresh tokens.

El workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) ejecuta pruebas, SpotBugs con severidad media, escaneo de vulnerabilidades de las dependencias de producción y detección de secretos en cada `push` y `pull_request`. Todas las acciones de terceros están fijadas por SHA y el Maven Wrapper verifica el SHA-256 de la distribución antes de ejecutarla.

## Construcción

Windows:

```powershell
./mvnw.cmd clean package
java -jar target/inventory-api-0.0.1-SNAPSHOT.jar
```

En macOS o Linux usa `./mvnw` y separadores de ruta `/`. PostgreSQL y las variables de las claves RSA deben estar disponibles al ejecutar el JAR.

## Estructura principal

```text
src/main/java/com/example/inventory/
├── auth/          Login, refresh, logout, sesión y cambio de contraseña
├── config/        Configuración de OpenAPI
├── counts/        Conteos físicos, snapshots, variaciones y publicación
├── customers/     Clientes, búsqueda y desactivación lógica
├── inventory/     Existencias, reservas, ajustes, kardex y alertas
├── orders/        Pedidos, precios históricos y ciclo transaccional
├── products/      Catálogo, stock mínimo y borrado lógico
├── purchases/     Órdenes de compra, recepciones parciales e idempotencia
├── security/      JWT, CORS y autorización
├── shared/        Excepciones, errores y paginación
├── suppliers/     Proveedores y asociaciones de abastecimiento
├── transfers/     Transferencias, tránsito e idempotencia entre almacenes
└── users/         Cuentas, roles y bootstrap

src/main/resources/
├── application.yml
└── db/migration/  Migraciones Flyway

src/test/java/com/example/inventory/
└── ...            Pruebas unitarias y de integración
```

## Alcance pendiente

- Estrategia concreta de métricas, salud y observabilidad operativa.
- Operación de rotación, respaldo y monitoreo de claves.

## Detener el entorno local

Conservar los datos:

```bash
docker compose down
```

Eliminar también el volumen local:

```bash
docker compose down -v
```

El segundo comando elimina permanentemente la base de datos local de Docker Compose.

## Solución de problemas

### Testcontainers no encuentra Docker

Inicia Docker Desktop o el servicio Docker. `docker version` debe mostrar tanto el cliente como el servidor.

### El puerto 5432 está ocupado

Cambia el puerto de `compose.yaml`, por ejemplo a `127.0.0.1:5433:5432`, y configura `DB_URL=jdbc:postgresql://localhost:5433/inventory`.

### No se encuentran las claves JWT

Comprueba que ambas rutas existan, incluyan el prefijo `file:` y tengan permiso de lectura. La aplicación no inicia si falta alguna clave.

### No se encuentra Java

Verifica que el JDK 25 LTS esté instalado, que `JAVA_HOME` apunte al JDK y que su carpeta `bin` esté en `PATH`.
