# Inventario API

API REST de productos e inventario con autenticación JWT y autorización por roles. Usa Java, Spring Boot y PostgreSQL en un monolito modular.

## Funcionalidades actuales

<<<<<<< HEAD
- CRUD de productos con SKU único y borrado lógico.
- Consulta y ajustes de inventario con bloqueo pesimista, saldo no negativo y movimientos internos.
- Login por usuario o correo.
- Access tokens JWT RS256 de corta duración.
- Refresh tokens opacos almacenados como hash, con expiración, rotación, revocación y detección de reutilización.
- Roles `ADMIN`, `INVENTORY_MANAGER` y `SALES`.
- Administración de usuarios por `ADMIN`.
- Flyway y pruebas PostgreSQL con Testcontainers.
=======
- Crear, consultar, actualizar y eliminar lógicamente productos.
- Consultar las existencias de un producto.
- Registrar entradas y salidas de inventario de forma atómica.
- Mantener un kardex con el tipo de movimiento, cambio de cantidad, saldo anterior,
  saldo resultante, referencia de negocio, fecha y usuario responsable.
- Conservar el inventario y sus movimientos cuando un producto se elimina.
- Proveer operaciones internas para descontar y restaurar existencias al confirmar o
  cancelar pedidos.
- Impedir que las existencias queden en números negativos.
- Serializar los ajustes concurrentes mediante bloqueo pesimista del inventario.
- Validar solicitudes y devolver errores HTTP estructurados.
- Crear y validar el esquema de PostgreSQL mediante Flyway.
- Documentar y probar la API desde Swagger UI.
- Ejecutar pruebas de integración contra PostgreSQL mediante Testcontainers.
>>>>>>> f6ffd9e28770e21fec4f6ee147d34c407d2ccc45

No existe API de pedidos: sus tablas y operaciones internas son infraestructura parcial, no un módulo terminado. Tampoco existe endpoint para consultar movimientos de inventario.

## Tecnologías y requisitos

Java 24, Spring Boot 4.0.7, Spring Security Resource Server, Nimbus JOSE/JWT, PostgreSQL 17.5 Alpine, Flyway, Spring Data JPA, Springdoc 3.0.2, JUnit, Mockito, Testcontainers, Maven Wrapper y Docker Compose.

Se requiere JDK 24 en `PATH`, Docker iniciado y claves RSA externas: pública X.509 y privada PKCS#8. Maven no requiere instalación.

```text
java -version
docker version
docker compose version
```

<<<<<<< HEAD
## Inicio local
=======
El motor de Docker debe estar iniciado antes de levantar PostgreSQL o ejecutar las pruebas.

## Obtener el proyecto
Git:

```bash
git clone <URL_DEL_REPOSITORIO>
cd Inventario
```

## Ejecución rápida

### 1. Iniciar PostgreSQL

Desde la raíz del proyecto:
>>>>>>> f6ffd9e28770e21fec4f6ee147d34c407d2ccc45

```bash
docker compose up -d
docker compose ps
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out inventory-private.pem
openssl pkey -in inventory-private.pem -pubout -out inventory-public.pem
```

Compose usa `localhost:5432` y base, usuario y contraseña `inventory`, exclusivamente locales. Genera las claves fuera del repositorio y no las agregues a Git.

No hay credenciales administrativas fijas. En una base sin usuarios habilita una sola vez el inicializador:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_USERNAME = "admin-local"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@example.test"
$env:BOOTSTRAP_ADMIN_PASSWORD = "<mínimo-12-caracteres>"
$env:JWT_PUBLIC_KEY_LOCATION = "file:C:/ruta-segura/inventory-public.pem"
$env:JWT_PRIVATE_KEY_LOCATION = "file:C:/ruta-segura/inventory-private.pem"
.\mvnw.cmd spring-boot:run
```

Solo crea el administrador cuando no existen usuarios. Después deshabilita el bootstrap y elimina la contraseña del entorno. En macOS/Linux usa `export`, rutas `file:/...` y `./mvnw`.

La API queda en `http://localhost:8080`. Swagger está deshabilitado por defecto. Con `SWAGGER_ENABLED=true`, sus rutas requieren `ADMIN`.

## Autenticación

| Método | Ruta | Acceso | Resultado |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Público | Emite access y refresh |
| `POST` | `/api/v1/auth/refresh` | Público, con refresh | Rota y emite otro par |
| `POST` | `/api/v1/auth/logout` | Público, con refresh | Revoca la familia |
| `GET` | `/api/v1/auth/me` | Autenticado | Identidad y roles |

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin-local","password":"<contraseña>"}'

curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'

curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

`identifier` acepta usuario o correo. La respuesta incluye `tokenType`, `accessToken`, `accessTokenExpiresAt`, `refreshToken` y `refreshTokenExpiresAt`. Fallos de credenciales y usuarios inexistentes, deshabilitados o bloqueados usan el mismo mensaje. Un refresh usado o revocado no se reutiliza. Logout revoca su familia. Un access token sigue válido hasta `exp`; no existe denylist.

## Matriz de roles

<<<<<<< HEAD
| Operación implementada | `ADMIN` | `INVENTORY_MANAGER` | `SALES` |
|---|---:|---:|---:|
| Administrar usuarios y roles | Sí | No | No |
| Crear, modificar o eliminar productos | Sí | Sí | No |
| Ajustar inventario | Sí | Sí | No |
| Consultar productos e inventario | Sí | Sí | Sí |
| Swagger habilitado | Sí | No | No |
=======
Al iniciar la aplicación, Flyway aplicará automáticamente las migraciones pendientes e Hibernate validará el esquema.
>>>>>>> f6ffd9e28770e21fec4f6ee147d34c407d2ccc45

Clientes y pedidos no aparecen porque no tienen endpoints.

## Endpoints de negocio

Todos requieren `Authorization: Bearer <ACCESS_TOKEN>`.

| Método | Ruta | Acceso |
|---|---|---|
| `GET` | `/api/products`, `/api/products/{id}` | Autenticado |
| `POST` | `/api/products` | `ADMIN`, `INVENTORY_MANAGER` |
| `PUT`, `DELETE` | `/api/products/{id}` | `ADMIN`, `INVENTORY_MANAGER` |
| `GET` | `/api/inventory/{productId}` | Autenticado |
| `PATCH` | `/api/inventory/{productId}/adjustments` | `ADMIN`, `INVENTORY_MANAGER` |

`400`: entrada inválida o saldo negativo; `401`: autenticación ausente/inválida; `403`: permiso insuficiente; `404`: recurso inexistente; `409`: duplicado.

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"sku":"KBD-001","name":"Teclado","description":null,"price":1299.90,"active":true}'

<<<<<<< HEAD
=======
La respuesta incluye el identificador `id` del producto. Sustituye `<PRODUCT_ID>` en los siguientes ejemplos.

Los SKU se normalizan automáticamente: se eliminan los espacios exteriores y se
guardan en mayúsculas. No se permiten SKU repetidos, sin distinguir entre
mayúsculas y minúsculas.

### Listar productos

```bash
curl http://localhost:8080/api/products
```

### Consultar un producto

```bash
curl http://localhost:8080/api/products/<PRODUCT_ID>
```

### Consultar sus existencias

```bash
curl http://localhost:8080/api/inventory/<PRODUCT_ID>
```

### Agregar existencias

```bash
>>>>>>> f6ffd9e28770e21fec4f6ee147d34c407d2ccc45
curl -X PATCH http://localhost:8080/api/inventory/<PRODUCT_ID>/adjustments \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"quantityDelta":10}'
```

## Administración de usuarios

Todos requieren `ADMIN`.

<<<<<<< HEAD
| Método | Ruta | Acción |
=======
La operación será rechazada con HTTP `400` si el descuento intenta dejar el inventario en negativo.

Cada ajuste exitoso se registra también en `stock_movements`. El primer ingreso de
un producto se clasifica como `INITIAL_STOCK`; los ingresos posteriores como
`MANUAL_IN`, y las salidas como `MANUAL_OUT`. El historial se conserva aunque el
producto sea eliminado.

### Eliminar un producto

```bash
curl -X DELETE http://localhost:8080/api/products/<PRODUCT_ID>
```

La respuesta es HTTP `204`. La eliminación es lógica: el producto deja de aparecer
en listados y consultas, y sus operaciones de inventario pasan a responder HTTP
`404`, pero sus datos y movimientos históricos permanecen en PostgreSQL. El SKU
del producto eliminado continúa reservado.

## Endpoints disponibles

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/products` | Lista productos no eliminados, ordenados por nombre |
| `GET` | `/api/products/{id}` | Consulta un producto no eliminado |
| `POST` | `/api/products` | Crea un producto |
| `PUT` | `/api/products/{id}` | Reemplaza los campos editables de un producto |
| `DELETE` | `/api/products/{id}` | Elimina lógicamente un producto |
| `GET` | `/api/inventory/{productId}` | Consulta existencias; devuelve cero si aún no hay registro |
| `PATCH` | `/api/inventory/{productId}/adjustments` | Aplica una entrada o salida y registra el movimiento |

Actualmente no hay un endpoint público para consultar el kardex. Los movimientos
se almacenan como trazabilidad interna y quedan disponibles para futuros casos de
uso o endpoints.

## Preparación para pedidos

La migración `V2` crea las tablas `orders`, `order_items` y `stock_movements`. El
módulo de inventario publica además el contrato interno `InventoryOperations` para:

- `consumeForOrder`: descontar existencias y registrar `ORDER_CONFIRMED`.
- `restoreForOrder`: devolver existencias y registrar `ORDER_CANCELLED`.

Ambas operaciones validan cantidades positivas, bloquean el inventario durante la
escritura y guardan la referencia del pedido y el usuario responsable. La
orquestación del ciclo de vida del pedido y sus endpoints REST todavía no están
implementados.

## Ejecutar las pruebas

Las pruebas utilizan Testcontainers para crear una instancia temporal y aislada de PostgreSQL. Docker debe estar iniciado; no es necesario ejecutar `docker compose up` previamente.

Windows:

```powershell
.\mvnw.cmd test
```

macOS o Linux:

```bash
./mvnw test
```

## Construir el ejecutable

Windows:

```powershell
.\mvnw.cmd clean package
java -jar target\inventory-api-0.0.1-SNAPSHOT.jar
```

macOS o Linux:

```bash
./mvnw clean package
java -jar target/inventory-api-0.0.1-SNAPSHOT.jar
```

PostgreSQL debe continuar disponible mientras se ejecuta el archivo JAR.

## Configuración

La aplicación acepta estas variables de entorno:

| Variable | Valor predeterminado | Descripción |
>>>>>>> f6ffd9e28770e21fec4f6ee147d34c407d2ccc45
|---|---|---|
| `POST`, `GET` | `/api/v1/users` | Crear o listar |
| `GET` | `/api/v1/users/{id}` | Consultar |
| `PATCH` | `/api/v1/users/{id}/status` | Cambiar `enabled` y `locked` |
| `PUT` | `/api/v1/users/{id}/roles` | Reemplazar roles |

```json
{"username":"sales.one","email":"sales.one@example.test","password":"<mínimo-12-caracteres>","roles":["SALES"]}
```

Las respuestas nunca incluyen contraseña ni hash. Username y correo son únicos sin distinguir mayúsculas. Se protege al último administrador activo.

## Variables

| Variable | Predeterminado | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/inventory` | JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `inventory` | Credenciales locales |
| `JWT_PUBLIC_KEY_LOCATION` | Requerida | Pública X.509 |
| `JWT_PRIVATE_KEY_LOCATION` | Requerida | Privada PKCS#8 |
| `JWT_ISSUER` | `inventory-api` | Claim `iss` |
| `JWT_AUDIENCE` | `inventory-clients` | Claim `aud` |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | TTL access; permitido de 1 minuto a 1 hora |
| `JWT_REFRESH_TOKEN_TTL` | `14d` | TTL refresh; permitido de 1 hora a 90 días |
| `CORS_ALLOWED_ORIGINS` | Lista vacía | Orígenes separados por coma |
| `SWAGGER_ENABLED` | `false` | Swagger solo `ADMIN` |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` | Inicializador |
| `BOOTSTRAP_ADMIN_USERNAME` | Sin valor | Usuario inicial |
| `BOOTSTRAP_ADMIN_EMAIL` | Sin valor | Correo inicial |
| `BOOTSTRAP_ADMIN_PASSWORD` | Sin valor | Contraseña inicial |

Producción debe usar secretos externos. CORS no admite credenciales, rechaza el comodín `*` y sin orígenes rechaza cross-origin. Bearer y refresh viajan en encabezado/JSON, no cookies: la API es stateless y CSRF está deshabilitado. Si refresh pasa a cookie, CSRF debe reactivarse y probarse.

## Pruebas

Docker debe estar iniciado; Testcontainers crea PostgreSQL temporal.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd verify
```

La suite contiene 75 pruebas: 62 unitarias y 13 de integración. `verify` genera `target/inventory-api-0.0.1-SNAPSHOT.jar`.

## Afirmación → evidencia → estado

<<<<<<< HEAD
| Afirmación | Evidencia | Prueba | Estado |
|---|---|---|---|
| CRUD y borrado lógico | `ProductController`, `ProductService`, V1/V3 | Productos e integración | Cubierto |
| Inventario no negativo y bloqueado | `InventoryService`, `InventoryRepository`, V1/V2 | Inventario | Cubierto; sin prueba concurrente |
| Login genérico usuario/correo | `AuthService`, `InventoryUserDetailsService` | `AuthServiceTest` e integración | Cubierto |
| JWT RS256 valida firma, algoritmo, `iss`, `aud`, `exp`, `sub`, `iat`, `jti` y roles | `SecurityConfiguration`, `JwtService` | `JwtSecurityTest` | Cubierto |
| Refresh hasheado, rotado, revocado y replay detection | `RefreshTokenService`, V4 | Unitarias e integración | Cubierto |
| Roles, 401 y 403 | `SecurityConfiguration`, `@PreAuthorize` | Integración | Cubierto |
| Usuarios sin exponer hashes | `UserController`, `UserAdministrationService` | Unitarias e integración | Cubierto |
| V4 crea usuarios, roles, relaciones e índices | Migración V4 | Integración | Cubierto |
| Swagger cerrado por defecto | Configuración de seguridad | Integración | Cubierto; modo habilitado sin prueba dedicada |
| Historial por API | Sin controlador | Sin prueba HTTP | Roadmap |
| Pedidos | Infraestructura V2 | Sin servicio/API | Parcial |
| Revocación inmediata de access | Sin denylist | Sin prueba | No implementado |

## Parcial o no verificado

- API de pedidos y clientes.
- Consulta HTTP de movimientos.
- Prueba concurrente dedicada de ajustes.
- Swagger habilitado y autenticado como `ADMIN`.
- Revocación inmediata de access tokens e invalidación instantánea de roles emitidos.
- Operación productiva de rotación, monitoreo y respaldo de claves.
=======
## Detener el entorno local

Para detener PostgreSQL sin eliminar la información almacenada:

```bash
docker compose down
```

Para detenerlo y eliminar también el volumen local de datos:

```bash
docker compose down -v
```

> El segundo comando elimina permanentemente la base de datos local creada por Docker Compose.

## Estructura principal

```text
src/main/java/com/example/inventory/
├── config/       Configuración de OpenAPI
├── inventory/    Existencias, ajustes, kardex y contrato para pedidos
├── products/     Catálogo y eliminación lógica de productos
└── shared/       Excepciones y respuestas de error compartidas

src/main/resources/
├── application.yml
└── db/migration/ Migraciones de Flyway
```

## Solución de problemas

### Testcontainers no encuentra Docker

Si aparece `Could not find a valid Docker environment`, inicia Docker Desktop o el servicio Docker y verifica nuevamente:

```bash
docker version
```

La salida debe mostrar información tanto de `Client` como de `Server`.

### El puerto 5432 está ocupado

Detén la instancia local de PostgreSQL que utiliza ese puerto o cambia el puerto publicado en `compose.yaml`. Si se publica, por ejemplo, `5433:5432`, inicia la aplicación con:

```text
DB_URL=jdbc:postgresql://localhost:5433/inventory
```

### La aplicación no encuentra Java

Comprueba que el JDK 24 esté instalado, que `JAVA_HOME` apunte al JDK y que su carpeta `bin` esté incluida en `PATH`.

## Estado del proyecto

El catálogo de productos, la eliminación lógica, el control concurrente de
existencias y el registro del kardex están implementados. La base de datos y el
contrato interno de inventario ya preparan la confirmación y cancelación de pedidos,
pero aún falta el módulo que orqueste y exponga ese flujo. La seguridad con JWT y la
autorización por roles también permanecen como siguientes incrementos funcionales.
>>>>>>> f6ffd9e28770e21fec4f6ee147d34c407d2ccc45
