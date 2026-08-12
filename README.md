# Inventario API

API REST para administrar productos, existencias y usuarios. Está construida como un monolito modular con Java, Spring Boot y PostgreSQL, y protege sus operaciones mediante JWT y autorización por roles.

## Estado actual

El proyecto implementa:

- CRUD de productos con SKU único, normalización a mayúsculas y borrado lógico.
- Consulta y ajustes atómicos de inventario, sin permitir existencias negativas.
- Bloqueo pesimista durante las modificaciones de stock.
- Registro interno de movimientos de inventario (kardex).
- Autenticación por nombre de usuario o correo electrónico.
- Access tokens JWT firmados con RS256.
- Refresh tokens opacos, almacenados como hash, con rotación, revocación por familia y detección de reutilización.
- Roles `ADMIN`, `INVENTORY_MANAGER` y `SALES`.
- Administración de usuarios reservada a `ADMIN`.
- Migraciones de base de datos con Flyway.
- Pruebas unitarias y de integración con PostgreSQL mediante Testcontainers.

Las tablas y el contrato interno para confirmar o cancelar pedidos ya existen, pero no hay una API de pedidos. Tampoco existe todavía un endpoint para consultar los movimientos de inventario.

## Tecnologías

- Java 24
- Spring Boot 4.0.7
- Spring Web MVC, Spring Data JPA y Spring Security
- OAuth2 Resource Server y Nimbus JOSE/JWT
- PostgreSQL 17.5 Alpine
- Flyway
- Springdoc OpenAPI 3.0.2
- Maven Wrapper
- JUnit, Mockito y Testcontainers
- Docker Compose

## Requisitos

- JDK 24 disponible en `PATH`.
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

El entorno local publica PostgreSQL en `localhost:5432` y usa `inventory` como nombre de base, usuario y contraseña. Estas credenciales son únicamente para desarrollo local.

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

`identifier` acepta el nombre de usuario o el correo. La respuesta incluye `tokenType`, `accessToken`, `accessTokenExpiresAt`, `refreshToken` y `refreshTokenExpiresAt`.

Envía el access token como `Authorization: Bearer <ACCESS_TOKEN>`. Para renovar o cerrar la sesión, envía `{"refreshToken":"<REFRESH_TOKEN>"}` como JSON a la ruta correspondiente.

Los fallos de login, los usuarios inexistentes, deshabilitados o bloqueados y los refresh tokens inválidos al renovar usan la misma respuesta genérica `401`. Un refresh token rotado o revocado no puede reutilizarse. El logout revoca toda su familia y es idempotente: un token desconocido también recibe `204`.

Los access tokens son stateless y siguen válidos hasta expirar. No existe una denylist, por lo que un logout o cambio de roles no invalida de inmediato un access token ya emitido.

## Roles y permisos

| Operación | `ADMIN` | `INVENTORY_MANAGER` | `SALES` |
|---|:---:|:---:|:---:|
| Consultar productos e inventario | Sí | Sí | Sí |
| Crear, modificar o eliminar productos | Sí | Sí | No |
| Ajustar inventario | Sí | Sí | No |
| Administrar usuarios y roles | Sí | No | No |
| Acceder a OpenAPI/Swagger habilitado | Sí | No | No |

## Productos e inventario

Todos estos endpoints requieren un access token.

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| `GET` | `/api/products` | Autenticado | Lista productos no eliminados por nombre |
| `GET` | `/api/products/{id}` | Autenticado | Consulta un producto no eliminado |
| `POST` | `/api/products` | `ADMIN`, `INVENTORY_MANAGER` | Crea un producto |
| `PUT` | `/api/products/{id}` | `ADMIN`, `INVENTORY_MANAGER` | Reemplaza los campos editables |
| `DELETE` | `/api/products/{id}` | `ADMIN`, `INVENTORY_MANAGER` | Elimina lógicamente; responde `204` |
| `GET` | `/api/inventory/{productId}` | Autenticado | Consulta las existencias |
| `PATCH` | `/api/inventory/{productId}/adjustments` | `ADMIN`, `INVENTORY_MANAGER` | Suma o resta existencias |

Producto de ejemplo:

```json
{"sku":"KBD-001","name":"Teclado","description":null,"price":1299.90,"active":true}
```

El SKU se recorta y guarda en mayúsculas. Su unicidad no distingue mayúsculas y el SKU de un producto eliminado continúa reservado.

Para ajustar inventario envía `{"quantityDelta":10}`. El valor debe ser distinto de cero: uno positivo registra una entrada y uno negativo una salida. La operación responde `400` si deja el saldo en negativo. Si aún no hay registro de inventario, la consulta devuelve cantidad `0` y `updatedAt: null`.

Cada ajuste exitoso genera un movimiento interno con saldo anterior y posterior, tipo y referencia. La primera entrada usa `INITIAL_STOCK`; las siguientes, `MANUAL_IN` o `MANUAL_OUT`. El historial permanece tras eliminar el producto, pero aún no se expone por HTTP.

## Administración de usuarios

Todas estas rutas requieren el rol `ADMIN`.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/users` | Crea un usuario |
| `GET` | `/api/v1/users` | Lista usuarios por nombre |
| `GET` | `/api/v1/users/{id}` | Consulta un usuario |
| `PATCH` | `/api/v1/users/{id}/status` | Cambia `enabled` y `locked` |
| `PUT` | `/api/v1/users/{id}/roles` | Reemplaza todos los roles |

Ejemplo de creación:

```json
{"username":"sales.one","email":"sales.one@example.test","password":"<mínimo-12-caracteres>","roles":["SALES"]}
```

El usuario y correo se normalizan a minúsculas y deben ser únicos. Los roles no pueden quedar vacíos. Las respuestas nunca incluyen contraseña ni hash, y el servicio protege al último administrador activo. No hay endpoint para cambiar contraseñas.

## Errores HTTP

Los errores usan los campos `timestamp`, `status`, `error`, `message`, `path` y `validationErrors`.

- `400`: cuerpo inválido, ajuste cero o inventario insuficiente.
- `401`: autenticación ausente o inválida.
- `403`: rol insuficiente.
- `404`: producto o usuario inexistente.
- `409`: identidad o SKU duplicado, o protección del último administrador.

## OpenAPI y Swagger UI

La documentación está deshabilitada por defecto. Define `SWAGGER_ENABLED=true` y reinicia la aplicación para habilitar:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

Ambas rutas siguen protegidas y requieren un access token con rol `ADMIN`.

## Configuración

| Variable | Predeterminado | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/inventory` | URL JDBC |
| `DB_USERNAME` | `inventory` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `inventory` | Contraseña de PostgreSQL |
| `JWT_PUBLIC_KEY_LOCATION` | Requerida | Clave pública X.509 |
| `JWT_PRIVATE_KEY_LOCATION` | Requerida | Clave privada PKCS#8 |
| `JWT_ISSUER` | `inventory-api` | Claim `iss` |
| `JWT_AUDIENCE` | `inventory-clients` | Claim `aud` |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | TTL de access; entre 1 minuto y 1 hora |
| `JWT_REFRESH_TOKEN_TTL` | `14d` | TTL de refresh; entre 1 hora y 90 días |
| `CORS_ALLOWED_ORIGINS` | Lista vacía | Orígenes separados por comas |
| `SWAGGER_ENABLED` | `false` | Habilita Swagger para `ADMIN` |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` | Habilita el administrador inicial |
| `BOOTSTRAP_ADMIN_USERNAME` | Sin valor | Usuario inicial |
| `BOOTSTRAP_ADMIN_EMAIL` | Sin valor | Correo inicial |
| `BOOTSTRAP_ADMIN_PASSWORD` | Sin valor | Contraseña inicial, 12 a 128 caracteres |

En producción usa un gestor de secretos. CORS no admite credenciales ni el comodín `*`; sin orígenes configurados rechaza cross-origin. La autenticación usa Bearer y JSON, no cookies, por lo que la API es stateless y CSRF está deshabilitado.

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

Actualmente hay 75 pruebas: 62 unitarias y 13 de integración. Cubren productos, inventario, usuarios, autenticación, refresh tokens, JWT y autorización HTTP.

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
├── auth/          Login, refresh, logout y sesión actual
├── config/        Configuración de OpenAPI
├── inventory/     Existencias, movimientos y contrato para pedidos
├── products/      Catálogo y borrado lógico
├── security/      JWT, CORS y autorización
├── shared/        Excepciones y errores
└── users/         Cuentas, roles y bootstrap

src/main/resources/
├── application.yml
└── db/migration/  Migraciones Flyway

src/test/java/com/example/inventory/
└── ...            Pruebas unitarias y de integración
```

## Alcance pendiente

- API y ciclo de vida de pedidos.
- Endpoint de consulta del kardex.
- Revocación inmediata de access tokens y cambios de rol instantáneos.
- Prueba dedicada de ajustes concurrentes.
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

Cambia el puerto de `compose.yaml`, por ejemplo a `5433:5432`, y configura `DB_URL=jdbc:postgresql://localhost:5433/inventory`.

### No se encuentran las claves JWT

Comprueba que ambas rutas existan, incluyan el prefijo `file:` y tengan permiso de lectura. La aplicación no inicia si falta alguna clave.

### No se encuentra Java

Verifica que el JDK 24 esté instalado, que `JAVA_HOME` apunte al JDK y que su carpeta `bin` esté en `PATH`.
