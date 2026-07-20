# Inventario API

API REST para administrar un catálogo de productos y sus existencias. Está construida con Java, Spring Boot y PostgreSQL siguiendo una arquitectura de monolito modular.

## Funcionalidades actuales

- Crear, consultar, actualizar y eliminar productos.
- Consultar las existencias de un producto.
- Registrar entradas y salidas de inventario de forma atómica.
- Impedir que las existencias queden en números negativos.
- Validar solicitudes y devolver errores HTTP estructurados.
- Crear y validar el esquema de PostgreSQL mediante Flyway.
- Documentar y probar la API desde Swagger UI.
- Ejecutar pruebas de integración contra PostgreSQL mediante Testcontainers.

## Tecnologías

- Java 24
- Spring Boot 4
- Maven Wrapper
- PostgreSQL 17
- Flyway
- Spring Data JPA
- Springdoc OpenAPI
- JUnit y Testcontainers
- Docker Compose

## Requisitos

Para ejecutar el proyecto en Windows, macOS o Linux se necesita:

1. **Java JDK 24** instalado y disponible en la variable `PATH`.
2. **Docker Desktop** o Docker Engine con el complemento Docker Compose.
3. Git, únicamente si se va a clonar el repositorio.

No es necesario instalar Maven: el proyecto incluye Maven Wrapper (`mvnw` y `mvnw.cmd`).

Verifica las herramientas con:

```text
java -version
docker version
docker compose version
```

El motor de Docker debe estar iniciado antes de levantar PostgreSQL o ejecutar las pruebas.

## Obtener el proyecto

Si el proyecto está publicado en un repositorio Git:

```bash
git clone <URL_DEL_REPOSITORIO>
cd Inventario
```

Si se recibió como archivo comprimido, basta con extraerlo y abrir una terminal dentro de la carpeta que contiene `pom.xml` y `compose.yaml`.

## Ejecución rápida

### 1. Iniciar PostgreSQL

Desde la raíz del proyecto:

```bash
docker compose up -d
```

Comprueba que el contenedor esté en ejecución y saludable:

```bash
docker compose ps
```

La configuración local crea automáticamente:

| Propiedad | Valor local |
|---|---|
| Servidor | `localhost` |
| Puerto | `5432` |
| Base de datos | `inventory` |
| Usuario | `inventory` |
| Contraseña | `inventory` |

Estas credenciales son exclusivamente para desarrollo local.

### 2. Iniciar la aplicación

En Windows PowerShell o Símbolo del sistema:

```powershell
.\mvnw.cmd spring-boot:run
```

En macOS o Linux:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

Cuando aparezca el mensaje de inicio de Spring Boot, la API estará disponible en:

- API: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Especificación OpenAPI: `http://localhost:8080/v3/api-docs`

Al iniciar la aplicación, Flyway aplicará automáticamente las migraciones pendientes y Hibernate validará el esquema.

## Probar la API

### Crear un producto

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "KBD-001",
    "name": "Teclado mecánico",
    "description": "Teclado compacto con interruptores táctiles",
    "price": 1299.90,
    "active": true
  }'
```

La respuesta incluye el identificador `id` del producto. Sustituye `<PRODUCT_ID>` en los siguientes ejemplos.

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
curl -X PATCH http://localhost:8080/api/inventory/<PRODUCT_ID>/adjustments \
  -H "Content-Type: application/json" \
  -d '{"quantityDelta": 10}'
```

### Descontar existencias

```bash
curl -X PATCH http://localhost:8080/api/inventory/<PRODUCT_ID>/adjustments \
  -H "Content-Type: application/json" \
  -d '{"quantityDelta": -3}'
```

La operación será rechazada con HTTP `400` si el descuento intenta dejar el inventario en negativo.

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
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/inventory` | URL JDBC de PostgreSQL |
| `DB_USERNAME` | `inventory` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `inventory` | Contraseña de PostgreSQL |

Ejemplo en Windows PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://servidor:5432/inventory"
$env:DB_USERNAME = "usuario"
$env:DB_PASSWORD = "contraseña"
.\mvnw.cmd spring-boot:run
```

Ejemplo en macOS o Linux:

```bash
export DB_URL="jdbc:postgresql://servidor:5432/inventory"
export DB_USERNAME="usuario"
export DB_PASSWORD="contraseña"
./mvnw spring-boot:run
```

No se deben guardar credenciales reales dentro del repositorio.

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
├── inventory/    Consultas y ajustes de existencias
├── products/     Catálogo de productos
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

El catálogo de productos y el control básico de inventario están implementados. La seguridad con JWT, la autorización por roles y el flujo transaccional de pedidos están previstos como siguientes incrementos funcionales.
