# Inventario API

API REST de productos e inventario con autenticación JWT y autorización por roles. Usa Java, Spring Boot y PostgreSQL en un monolito modular.

## Funcionalidades actuales

- CRUD de productos con SKU único y borrado lógico.
- Consulta y ajustes de inventario con bloqueo pesimista, saldo no negativo y movimientos internos.
- Login por usuario o correo.
- Access tokens JWT RS256 de corta duración.
- Refresh tokens opacos almacenados como hash, con expiración, rotación, revocación y detección de reutilización.
- Roles `ADMIN`, `INVENTORY_MANAGER` y `SALES`.
- Administración de usuarios por `ADMIN`.
- Flyway y pruebas PostgreSQL con Testcontainers.

No existe API de pedidos: sus tablas y operaciones internas son infraestructura parcial, no un módulo terminado. Tampoco existe endpoint para consultar movimientos de inventario.

## Tecnologías y requisitos

Java 24, Spring Boot 4.0.7, Spring Security Resource Server, Nimbus JOSE/JWT, PostgreSQL 17.5 Alpine, Flyway, Spring Data JPA, Springdoc 3.0.2, JUnit, Mockito, Testcontainers, Maven Wrapper y Docker Compose.

Se requiere JDK 24 en `PATH`, Docker iniciado y claves RSA externas: pública X.509 y privada PKCS#8. Maven no requiere instalación.

```text
java -version
docker version
docker compose version
```

## Inicio local

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

| Operación implementada | `ADMIN` | `INVENTORY_MANAGER` | `SALES` |
|---|---:|---:|---:|
| Administrar usuarios y roles | Sí | No | No |
| Crear, modificar o eliminar productos | Sí | Sí | No |
| Ajustar inventario | Sí | Sí | No |
| Consultar productos e inventario | Sí | Sí | Sí |
| Swagger habilitado | Sí | No | No |

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

curl -X PATCH http://localhost:8080/api/inventory/<PRODUCT_ID>/adjustments \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"quantityDelta":10}'
```

## Administración de usuarios

Todos requieren `ADMIN`.

| Método | Ruta | Acción |
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
