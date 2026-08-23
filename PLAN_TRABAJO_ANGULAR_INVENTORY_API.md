# Plan de trabajo actualizado: FrontEnd Angular para Inventory API

**Fecha de revisión:** 21 de agosto de 2026\
**Proyecto fuente:** `C:\Proyectos\Inventario`  
**Copia de trabajo:** `C:\Users\brand\OneDrive\Documentos\Inventario`  
**Rama:** `master`  
**Commit verificado:** `15de61f8085c55c01c61f0a18f8afe405378be76`  
**Estado:** API sincronizada y funcional; Fase 2 del FrontEnd completada con `F2-01`–`F2-08` verificadas.

## 1. Resultado ejecutivo

Inventory API está en condiciones funcionales de soportar un FrontEnd amplio. Desde la revisión anterior incorporó:

- versionado uniforme bajo `/api/v1`;
- listados globales de existencias y movimientos;
- errores con código estable y correlation ID;
- almacenes y configuración de inventario por almacén;
- proveedores y asociaciones proveedor-producto;
- órdenes de compra y recepciones idempotentes;
- transferencias entre almacenes;
- conteos físicos;
- rate limiting compartido para autenticación;
- OpenAPI reproducible;
- SpotBugs, Trivy y búsqueda de secretos en CI.

La API expone actualmente **81 operaciones HTTP distribuidas en 60 rutas OpenAPI**. La suite local aprobó **181 pruebas**, las **16 migraciones Flyway** y el análisis SpotBugs. El workflow CI #20 del commit auditado terminó correctamente, incluidos los escaneos de dependencias y secretos.

Los puntos que bloqueaban la sesión Angular quedaron resueltos:

1. la resolución de cliente acepta exclusivamente direcciones IP literales mediante `InetAddress.ofLiteral`, sin consultas DNS, y cuenta con pruebas de entradas ambiguas;
2. el MVP adoptó formalmente una sesión no persistente: access y refresh token viven sólo en memoria y una recarga exige login;
3. CORS local ya incluye `http://localhost:4200`;
4. Java 25 quedó fijado mediante `.java-version`, VS Code y `scripts/mvnw-jdk25.ps1`;
5. `.gitignore` excluye ambientes, claves privadas y almacenes de certificados, preservando sólo plantillas `.example`.

No se detectó un hallazgo de seguridad abierto que impida crear la base del FrontEnd. Permanecen tareas de madurez antes de producción: health/metrics, escaneo periódico ampliado, ensayo del runbook JWT y, posteriormente, rotación sin interrupciones con `kid`/JWKS o un mecanismo equivalente.

## 2. Sincronización y estado del proyecto

### Sincronización realizada

- La copia de trabajo avanzó nuevamente por fast-forward desde `a6b570a` hasta `15de61f` usando el repositorio local indicado.
- Posteriormente se actualizó `origin/master` desde GitHub.
- `HEAD` y `origin/master` apuntan al mismo commit.
- El repositorio fuente estaba limpio y alineado con su `origin/master`.
- El único archivo no versionado en la copia de trabajo es este plan.

### Cambios relevantes recibidos

Los dos commits incorporados desde la revisión del 15 de agosto modificaron 20 archivos, con 450 inserciones y 26 eliminaciones. Los cambios principales corresponden a:

- parser literal de IP del cliente y nuevas pruebas de seguridad;
- decisión de arquitectura para tokens del MVP Angular;
- CORS local para Angular CLI;
- Java 25 reproducible en PowerShell y VS Code;
- exclusiones preventivas de secretos y material criptográfico;
- runbook de rotación de claves JWT;
- eliminación de advertencias de compilación y configuración explícita del agente Mockito.

### Estado de compilación y pruebas

La verificación se ejecutó mediante el nuevo script del proyecto, que selecciona y valida Temurin 25.0.3 LTS antes de iniciar Maven:

```text
Tests run: 181, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Evidencia adicional:

- 169 archivos Java de producción compilados.
- 31 archivos Java de pruebas compilados.
- 20 repositorios JPA detectados.
- 16 migraciones Flyway validadas y aplicadas sobre PostgreSQL 17.5 mediante Testcontainers.
- JAR generado en `target/inventory-api-0.0.1-SNAPSHOT.jar`.
- OpenAPI generado en `target/openapi/inventory-api-v1.json`.
- SpotBugs: 0 errores y 0 advertencias con esfuerzo máximo y umbral medio.

### Entorno reproducible confirmado

- `.java-version` declara Java 25.
- `.vscode/settings.json` configura el JDK del workspace y de nuevas terminales.
- `scripts/mvnw-jdk25.ps1` valida Java 25 y ejecuta `verify` por defecto.
- El agente Mockito se declara mediante Surefire, sin auto-adjunción dinámica.
- Las advertencias anteriores de APIs obsoletas y operaciones genéricas no comprobadas fueron corregidas.

## 3. Arquitectura comprobada

### Tecnologías

- Java 25 LTS.
- Spring Boot 4.0.7.
- Spring Web MVC, Spring Data JPA y Spring Security.
- JWT RS256 y OAuth2 Resource Server.
- Refresh tokens opacos, rotados, revocables y almacenados como hash.
- PostgreSQL 17.5.
- Flyway V1–V16.
- Springdoc OpenAPI 3.0.2.
- Maven Wrapper 3.3.4 con Maven 3.9.16 y SHA-256 de la distribución.
- JUnit, Mockito y Testcontainers.
- SpotBugs 4.10.3.0.
- Trivy mediante `aquasecurity/trivy-action` fijada por SHA en el workflow auditado.

### Módulos de dominio

| Módulo | Capacidad actual |
|---|---|
| `auth` | login, refresh, logout, perfil, cambio de contraseña y rate limiting |
| `security` | JWT, CORS, autorización cerrada por defecto y revocación inmediata |
| `products` | CRUD, búsqueda, precio, ciclo activo/eliminado y protección de documentos pendientes |
| `warehouses` | catálogo de almacenes y desactivación controlada |
| `inventory` | saldos, reservas, ajustes, Kardex, alertas y configuración por almacén |
| `suppliers` | proveedores y asociaciones de abastecimiento |
| `purchases` | órdenes de compra, recepciones parciales, costos e idempotencia |
| `transfers` | transferencias, tránsito y recepción entre almacenes |
| `counts` | conteos físicos completos/selectivos y publicación de variaciones |
| `customers` | CRUD, búsqueda y desactivación lógica |
| `orders` | pedidos, precios históricos, reservas, confirmación y cancelación |
| `users` | usuarios, roles, estado, contraseña y revocación de sesiones |
| `shared` | paginación, errores, códigos y correlación |

### Reglas transaccionales ya cubiertas

- No se permite inventario negativo.
- Las modificaciones de saldo usan bloqueo y orden estable para evitar deadlocks previsibles.
- Se separan existencia física, reservada y disponible por almacén.
- Reservas, confirmaciones y cancelaciones de pedidos son atómicas.
- Recepciones de compra son idempotentes por referencia externa y contenido.
- Transferencias conservan origen, tránsito y destino sin duplicar unidades.
- Conteos físicos capturan snapshots y publican diferencias de forma idempotente.
- Los movimientos almacenan efectos físicos/reservados, saldos, referencia, almacén y actor.
- Los cambios sensibles de usuario invalidan access y refresh tokens anteriores.
- La autorización usa lista permitida explícita; rutas nuevas no configuradas reciben `403`.

## 4. Inventario real de rutas

### Resumen por controlador

| Grupo | Ruta base | Operaciones | Acceso principal | Módulo Angular |
|---|---|---:|---|---|
| Autenticación | `/api/v1/auth` | 5 | público/autenticado | sesión y perfil |
| Productos | `/api/v1/products` | 5 | todos; escritura inventario | productos |
| Inventario global | `/api/v1/inventory` | 6 | todos/gestores | inventario |
| Almacenes | `/api/v1/warehouses` | 5 | todos/gestores | almacenes |
| Inventario de almacén | `/api/v1/warehouses/{warehouseId}/inventory` | 8 | todos/gestores | existencias por almacén |
| Proveedores | `/api/v1/suppliers` | 8 | `ADMIN`, `INVENTORY_MANAGER` | proveedores |
| Compras | `/api/v1/purchase-orders` | 8 | `ADMIN`, `INVENTORY_MANAGER` | compras y recepciones |
| Transferencias | `/api/v1/inventory-transfers` | 7 | `ADMIN`, `INVENTORY_MANAGER` | transferencias |
| Conteos físicos | `/api/v1/inventory-counts` | 8 | `ADMIN`, `INVENTORY_MANAGER` | conteos |
| Clientes | `/api/v1/customers` | 5 | `ADMIN`, `SALES` | clientes |
| Pedidos | `/api/v1/orders` | 9 | `ADMIN`, `SALES` | ventas/pedidos |
| Usuarios | `/api/v1/users` | 7 | `ADMIN` | administración |
| **Total** | **60 paths OpenAPI** | **81** |  |  |

### Autenticación

| Método | Ruta | Función FrontEnd |
|---|---|---|
| `POST` | `/api/v1/auth/login` | iniciar sesión |
| `POST` | `/api/v1/auth/refresh` | rotar refresh y access token |
| `POST` | `/api/v1/auth/logout` | revocar la familia de refresh |
| `GET` | `/api/v1/auth/me` | obtener identidad, roles y almacén predeterminado |
| `PUT` | `/api/v1/auth/password` | cambiar contraseña propia |

### Productos e inventario global

| Método | Ruta | Función FrontEnd |
|---|---|---|
| `GET/POST` | `/api/v1/products` | listar/crear productos |
| `GET/PUT/DELETE` | `/api/v1/products/{id}` | consultar/editar/eliminar lógicamente |
| `GET` | `/api/v1/inventory` | listar saldos globales |
| `GET` | `/api/v1/inventory/{productId}` | saldo agregado de un producto |
| `GET` | `/api/v1/inventory/movements` | movimientos globales |
| `GET` | `/api/v1/inventory/{productId}/movements` | Kardex por producto |
| `GET` | `/api/v1/inventory/low-stock` | alertas globales |
| `PATCH` | `/api/v1/inventory/{productId}/adjustments` | ajuste compatible sobre almacén principal |

### Almacenes

| Método | Ruta | Función FrontEnd |
|---|---|---|
| `GET/POST` | `/api/v1/warehouses` | listar/crear almacenes |
| `GET/PUT/DELETE` | `/api/v1/warehouses/{id}` | consultar/editar/desactivar |
| `GET` | `/api/v1/warehouses/{id}/inventory` | saldos paginados del almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/{productId}` | saldo específico |
| `PATCH` | `/api/v1/warehouses/{id}/inventory/{productId}/adjustments` | ajuste por almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/movements` | Kardex del almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/low-stock` | alertas del almacén |
| `GET` | `/api/v1/warehouses/{id}/inventory/settings` | configuración de productos |
| `GET/PUT` | `/api/v1/warehouses/{id}/inventory/{productId}/settings` | mínimo y activación por almacén |

### Proveedores

| Método | Ruta | Función FrontEnd |
|---|---|---|
| `GET/POST` | `/api/v1/suppliers` | buscar/crear proveedor |
| `GET/PUT/DELETE` | `/api/v1/suppliers/{id}` | consultar/editar/desactivar |
| `GET` | `/api/v1/suppliers/{id}/products` | productos abastecidos |
| `PUT/DELETE` | `/api/v1/suppliers/{id}/products/{productId}` | asociar/desactivar producto |

### Compras y recepciones

| Método | Ruta | Función FrontEnd |
|---|---|---|
| `GET/POST` | `/api/v1/purchase-orders` | listar/crear borrador |
| `GET` | `/api/v1/purchase-orders/{id}` | detalle y pendientes |
| `PUT` | `/api/v1/purchase-orders/{id}/items` | editar borrador |
| `POST` | `/api/v1/purchase-orders/{id}/issue` | emitir orden |
| `POST` | `/api/v1/purchase-orders/{id}/receipts` | recepción parcial/total |
| `GET` | `/api/v1/purchase-orders/{id}/receipts` | historial de recepciones |
| `POST` | `/api/v1/purchase-orders/{id}/cancel` | cancelar sin recepciones |

### Transferencias

| Método | Ruta | Función FrontEnd |
|---|---|---|
| `GET/POST` | `/api/v1/inventory-transfers` | listar/crear borrador |
| `GET` | `/api/v1/inventory-transfers/{id}` | detalle y tránsito |
| `PUT` | `/api/v1/inventory-transfers/{id}/items` | editar borrador |
| `POST` | `/api/v1/inventory-transfers/{id}/dispatch` | despachar |
| `POST` | `/api/v1/inventory-transfers/{id}/receive` | recibir |
| `POST` | `/api/v1/inventory-transfers/{id}/cancel` | cancelar borrador |

### Conteos físicos

| Método | Ruta | Función FrontEnd |
|---|---|---|
| `GET/POST` | `/api/v1/inventory-counts` | listar/crear conteo |
| `GET` | `/api/v1/inventory-counts/{id}` | detalle, snapshots y diferencias |
| `POST` | `/api/v1/inventory-counts/{id}/open` | abrir conteo |
| `PUT` | `/api/v1/inventory-counts/{id}/lines/{productId}` | capturar cantidad física |
| `POST` | `/api/v1/inventory-counts/{id}/submit` | enviar conteo completo |
| `POST` | `/api/v1/inventory-counts/{id}/post` | publicar variaciones |
| `POST` | `/api/v1/inventory-counts/{id}/cancel` | cancelar no publicado |

### Clientes, pedidos y usuarios

- Clientes: 5 operaciones CRUD bajo `/api/v1/customers`.
- Pedidos: 9 operaciones bajo `/api/v1/orders` para listar, crear, editar artículos, reservar, liberar, confirmar, cancelar y eliminar.
- Usuarios: 7 operaciones bajo `/api/v1/users` para alta, consulta, estado, roles, contraseña y revocación de sesiones.

## 5. Auditoría de seguridad previa al FrontEnd

### Alcance de la revisión

Se revisaron:

- dependencias resueltas y artefacto compilado mediante Trivy en CI;
- secretos mediante Trivy;
- código Java mediante SpotBugs;
- configuración de Spring Security, CORS, CSRF y Swagger;
- validación JWT, revocación y refresh tokens;
- rate limiting y resolución de IP de cliente;
- manejo de errores y correlation ID;
- configuración local, Maven Wrapper y GitHub Actions;
- implicaciones de seguridad para una aplicación Angular.

### Resultados automatizados confirmados

| Control | Resultado | Alcance/limitación |
|---|---|---|
| Maven `verify` | 181/181 pruebas aprobadas | funcional, integración, concurrencia y autorización |
| SpotBugs | 0 hallazgos | umbral medio, esfuerzo máximo |
| Trivy dependencias | 0 hallazgos en política | severidades media/alta/crítica; ignora vulnerabilidades sin corrección disponible |
| Trivy secretos | 0 hallazgos | todas las severidades configuradas |
| GitHub Actions CI #20 | éxito | commit `15de61f`, duración aproximada 3m09s |
| OpenAPI contract test | aprobado | 60 rutas y 81 operaciones, todas bajo `/api/v1` |

No se detectaron vulnerabilidades conocidas corregibles de severidad media, alta o crítica en el escaneo del 17 de agosto de 2026. Esto no equivale a afirmar ausencia absoluta de vulnerabilidades: el workflow usa `ignore-unfixed: true`, no bloquea severidad baja y un escáner de dependencias no sustituye pruebas de penetración.

### Controles positivos observados

- JWT firmado con RS256 y validación de `issuer`, `audience`, `sub`, `jti`, vida máxima, roles y versión del token.
- Access tokens revocables inmediatamente mediante versión de seguridad consultada en base de datos.
- Refresh tokens opacos, almacenados como hash, rotados y revocados por familia.
- Respuestas de tokens con `Cache-Control: no-store` y `Pragma: no-cache`.
- Contraseñas codificadas con el mecanismo delegante de Spring Security.
- Login sin enumeración visible de usuario.
- Rate limiting compartido en PostgreSQL para login, refresh y logout.
- Proxies confiables configurables por IP/CIDR; sólo se aceptan IP literales, sin DNS, y las cabeceras reenviadas se ignoran si el peer no es confiable.
- CORS con lista explícita, sin comodín y sin credenciales.
- CSRF deshabilitado de forma coherente con bearer tokens enviados explícitamente en JSON/headers.
- Swagger deshabilitado por defecto y, al habilitarse, restringido a `ADMIN`.
- `anyRequest().denyAll()` como cierre de autorización.
- Entidades JPA no expuestas directamente.
- Validación DTO y consultas parametrizadas.
- Errores de restricciones sin detalles internos.
- Correlation ID limitado a un formato seguro antes de introducirse al MDC.
- Secretos y claves JWT requeridos fuera del repositorio, con exclusiones preventivas en `.gitignore`.
- PostgreSQL local expuesto solo en `127.0.0.1`.
- Acciones de CI fijadas por SHA y Maven Wrapper con checksum.

### Hallazgos cerrados y riesgos pendientes

#### SEC-01 — Parser de IP sin resolución DNS

**Severidad original:** media, condicionada al despliegue detrás de proxy.  
**Estado:** cerrado en `15de61f`.

`ClientAddressResolver` usa `InetAddress.ofLiteral`, valida IPv4 estricta y rechaza hostnames, zonas IPv6, listas excesivas y entradas ambiguas. `ClientAddressResolverTest` cubre 14 escenarios. El README exige que el reverse proxy sobrescriba el `X-Forwarded-For` recibido y que `AUTH_TRUSTED_PROXIES` permanezca vacío sin proxy.

#### SEC-02 — Estrategia de refresh token en el navegador

**Severidad original:** riesgo de integración alto si se persistía de forma insegura.  
**Estado:** decisión aceptada y documentada en `docs/decisions/SEC-02-browser-refresh-token.md`.

El MVP mantendrá ambos tokens exclusivamente en memoria. Está prohibido usar `localStorage`, `sessionStorage`, IndexedDB, Cache API, cookies accesibles desde JavaScript o persistencia de estado. Cada recarga o pestaña nueva requerirá login. El interceptor serializará renovaciones concurrentes, sustituirá el par de forma atómica y limpiará la sesión ante un `401` del refresh.

La persistencia futura requerirá una nueva decisión, un BFF o cookie `HttpOnly`, `Secure`, `SameSite`, protección CSRF probada y cambios controlados de CORS/contrato.

#### SEC-03 — Prevención de archivos sensibles en Git

**Severidad original:** baja/preventiva.  
**Estado:** cerrado en `15de61f`; el escaneo no encontró secretos comprometidos.

`.gitignore` excluye `.env`, claves PEM/KEY/P8/DER, PKCS#12, JKS/keystores, claves SSH y carpetas de secretos. Sólo permite plantillas `.example` sin credenciales reales.

#### SEC-04 — Rotación operativa de claves JWT

**Severidad:** media operativa antes de producción.  
**Estado:** runbook creado; rotación sin interrupciones pendiente.

`docs/runbooks/jwt-key-rotation.md` define preparación, respaldo, corte coordinado, validación, reversa y respuesta a compromiso para el único par actual. Antes de producción debe ensayarse. Como mejora posterior, implementar convivencia temporal de claves con `kid`/JWKS o mecanismo equivalente.

#### SEC-05 — Cobertura del escaneo de vulnerabilidades

**Severidad:** informativa.  
**Estado:** limitación de política.

Trivy ignora vulnerabilidades sin solución disponible y no bloquea severidad baja. Esto es razonable para CI, pero se necesita un reporte periódico completo para conocer deuda aceptada.

**Acción:** agregar un escaneo informativo programado con `ignore-unfixed: false`, conservar SBOM y revisar excepciones mensualmente.

### Brechas funcionales y operativas no bloqueantes para crear Angular

- Las ocho rutas de proveedores existen en código/OpenAPI, pero todavía no tienen una tabla propia en el README.
- Falta estrategia concreta de health checks, métricas y observabilidad.
- No existen endpoints agregados de dashboard, reportes o exportación; el MVP puede componer datos existentes y la Fase 8 decidirá si conviene agregarlos al backend.
- No existe auditoría general de cambios de negocio más allá del Kardex y la trazabilidad propia de documentos.

## 6. Compuerta de seguridad antes de iniciar Angular

La compuerta mínima para iniciar Angular quedó satisfecha:

- [x] Corregir SEC-01 y añadir sus pruebas.
- [x] Aprobar la sesión en memoria para el MVP (SEC-02).
- [x] Configurar Java 25 reproducible en el proyecto.
- [x] Añadir `http://localhost:4200` a CORS local.
- [x] Añadir exclusiones preventivas de archivos sensibles.
- [x] Regenerar OpenAPI y confirmar 60 rutas/81 operaciones.
- [x] Ejecutar `mvn verify` y SpotBugs localmente.
- [x] Confirmar CI verde con Trivy de dependencias y secretos en el commit base.
- [ ] Documentar las ocho rutas de proveedores en README; no bloquea el cliente generado desde OpenAPI.

SEC-04 y SEC-05 no bloquean el arranque del FrontEnd, pero son obligatorios antes de producción.

## 7. Arquitectura propuesta para Angular

### Base técnica

- Angular 22.x como referencia inicial, fijando versiones compatibles de Node.js y TypeScript.
- Componentes standalone y rutas lazy por área funcional.
- TypeScript estricto.
- Cliente generado a partir de `target/openapi/inventory-api-v1.json`.
- Angular Router, `HttpClient` e interceptores funcionales.
- Formularios reactivos.
- Signals para estado local y RxJS para flujos HTTP.
- Angular Material como base visual inicial, sujeto a identidad gráfica.
- Diseño responsive y WCAG 2.2 AA.
- CSP y Trusted Types en despliegue cuando la biblioteca visual elegida sea compatible.

### Estructura sugerida

```text
frontend/
  src/app/
    core/
      auth/
      config/
      guards/
      http/
    layout/
    shared/
    features/
      dashboard/
      products/
      warehouses/
      inventory/
      suppliers/
      purchases/
      transfers/
      inventory-counts/
      customers/
      orders/
      users/
      profile/
  public/
  environments/
```

Reglas:

- `core` se carga una sola vez y no depende de funcionalidades.
- `shared` no contiene reglas de negocio globales.
- Cada `feature` agrupa página, componentes, modelos y acceso a datos del dominio.
- La autorización de la API es la fuente de verdad; ocultar un botón no reemplaza permisos del servidor.
- La URL de API se inyecta mediante configuración de ambiente/runtime.
- El cliente OpenAPI se regenera en CI o mediante comando reproducible.

## 8. Funciones y rutas del FrontEnd

| Ruta Angular | Roles | Función |
|---|---|---|
| `/login` | público | iniciar sesión |
| `/dashboard` | todos | resumen según rol |
| `/products` | todos | catálogo y filtros |
| `/products/new` | `ADMIN`, `INVENTORY_MANAGER` | alta |
| `/products/:id` | todos | detalle |
| `/products/:id/edit` | `ADMIN`, `INVENTORY_MANAGER` | edición |
| `/warehouses` | todos | catálogo de almacenes |
| `/warehouses/:id/inventory` | todos | saldos del almacén |
| `/warehouses/:id/settings` | todos/gestores | mínimos y activación |
| `/warehouses/:id/movements` | `ADMIN`, `INVENTORY_MANAGER` | Kardex |
| `/inventory/low-stock` | `ADMIN`, `INVENTORY_MANAGER` | alertas |
| `/suppliers` | `ADMIN`, `INVENTORY_MANAGER` | catálogo y búsqueda |
| `/suppliers/:id` | `ADMIN`, `INVENTORY_MANAGER` | detalle y productos asociados |
| `/purchase-orders` | `ADMIN`, `INVENTORY_MANAGER` | listado y filtros |
| `/purchase-orders/new` | `ADMIN`, `INVENTORY_MANAGER` | borrador de compra |
| `/purchase-orders/:id` | `ADMIN`, `INVENTORY_MANAGER` | ciclo y recepciones |
| `/inventory-transfers` | `ADMIN`, `INVENTORY_MANAGER` | listado |
| `/inventory-transfers/:id` | `ADMIN`, `INVENTORY_MANAGER` | editar/despachar/recibir |
| `/inventory-counts` | `ADMIN`, `INVENTORY_MANAGER` | listado |
| `/inventory-counts/:id` | `ADMIN`, `INVENTORY_MANAGER` | captura y publicación |
| `/customers` | `ADMIN`, `SALES` | mantenimiento |
| `/customers/:id` | `ADMIN`, `SALES` | detalle/edición |
| `/orders` | `ADMIN`, `SALES` | listado y filtros |
| `/orders/new` | `ADMIN`, `SALES` | captura |
| `/orders/:id` | `ADMIN`, `SALES` | ciclo de pedido |
| `/admin/users` | `ADMIN` | administración |
| `/admin/users/:id` | `ADMIN` | roles, estado y sesiones |
| `/profile` | todos | identidad y contraseña |
| `/forbidden` | todos | acceso denegado |
| `/**` | todos | página no encontrada |

### Estados de interfaz obligatorios

Cada página debe contemplar:

- carga inicial;
- recarga/actualización;
- lista vacía;
- error recuperable;
- validación por campo;
- conflicto de negocio `409`;
- sesión vencida;
- rate limit `429` respetando `Retry-After`;
- permiso insuficiente;
- confirmación de acciones sensibles;
- prevención de doble envío;
- éxito explícito y refresco de datos.

## 9. Plan de trabajo por fases

Las duraciones son días-persona y se recalibrarán con el equipo. Backend y FrontEnd pueden avanzar en paralelo después de cerrar la Fase 1.

### Fase 0 — Sincronización, auditoría y línea base

**Estado:** completada.  
**Resultado:** commit `15de61f` sincronizado, 81 operaciones inventariadas, 181 pruebas y CI #20 aprobado.

**Criterio de salida alcanzado:** código, migraciones, OpenAPI y análisis estático reproducibles con Java 25.

---

### Fase 1 — Compuerta de seguridad e integración

**Estado:** completada el 17 de agosto de 2026.  
**Dependencia:** Fase 0.

**Actividades**

1. Parser de IP literal sin DNS y 14 pruebas: completado.
2. Contrato de sesión Angular sólo en memoria: completado y documentado.
3. Java 25 reproducible y CORS local para Angular: completado.
4. `.gitignore` para claves, ambientes y almacenes: completado.
5. OpenAPI regenerado y validado: completado.
6. Maven, SpotBugs, Trivy de dependencias y Trivy de secretos: aprobados.
7. Tabla README de proveedores: completada el 17 de agosto de 2026. La automatización del cliente TypeScript se trasladó a la Fase 2 y no bloquea el inicio.

**Criterio de salida**

Alcanzado: no quedan hallazgos P0 abiertos y existe un contrato de sesión que Angular puede implementar sin persistir credenciales.

---

### Fase 2 — Fundación Angular y sistema visual

**Duración:** 4–7 días.  
**Dependencia:** Fase 1.

**Actividades**

1. Crear `frontend/` con Angular, routing, estilos y pruebas.
2. Activar TypeScript estricto, lint, formato y presupuestos de bundle.
3. Implementar layout responsive, menú por rol, encabezado y breadcrumbs.
4. Definir tema, tipografía, contraste y componentes compartidos.
5. Integrar cliente OpenAPI y configuración de ambientes.
6. Implementar servicio de sesión sólo en memoria, login, refresh coordinado, logout, `/me`, guards e interceptor; ninguna capa puede persistir tokens.
7. Implementar errores 401/403/404/409/429 y correlation ID visible para soporte.
8. Añadir pruebas unitarias y E2E de sesión.

#### Cómo empezar y dar seguimiento con Codex

El 17 de agosto de 2026 se comprobó inicialmente que `frontend/` no existía y el trabajo comenzó por **F2-01**, sin adelantar autenticación, diseño final ni módulos de negocio. `F2-01`–`F2-08` quedaron completadas el 21 de agosto de 2026. Conviene pedir a Codex una entrega a la vez: un pedido útil identifica el comportamiento deseado, los archivos o documentos que debe consultar, las restricciones que debe conservar y las verificaciones que debe ejecutar.

Antes de cada entrega, Codex debe leer `AGENTS.md`, revisar el estado de Git y confirmar que la dependencia indicada está completa. Al terminar debe presentar los archivos modificados, las decisiones tomadas, los comandos ejecutados y cualquier verificación pendiente. No se marca una entrega como completada sólo porque el código exista: también debe contar con la evidencia mínima de la tabla.

Secuencia recomendada:

```text
F2-01 → F2-02 ─┬→ F2-03 ─┐
               └→ F2-04 ─┴→ F2-05 → F2-06 → F2-07 → F2-08
```

F2-03 y F2-04 pueden desarrollarse como cambios independientes después de F2-02. F2-08 cierra la fase, pero las pruebas unitarias correspondientes se escriben dentro de cada entrega y no se posponen hasta el final.

| ID | Entrega | Dependencia | Estado | Evidencia mínima para completar |
|---|---|---|---|---|
| `F2-01` | Proyecto Angular base | Fase 1 | Completado | `npm ci`; 2 archivos de prueba y 3 pruebas aprobadas; build de producción de 189.46 kB |
| `F2-02` | Calidad y límites técnicos | `F2-01` | Completado | `npm ci`; formato y lint aprobados; 2 archivos/3 pruebas; builds de 1.31 MB (desarrollo) y 189.46 kB (producción) dentro de budgets |
| `F2-03` | Layout y navegación por rol | `F2-02` | Completado | shell y política central implementados; `npm ci` y `npm run check` aprobados; 4 archivos/17 pruebas; builds de 1.32 MB y 228.97 kB; validación visual aprobada en 1440×900, 390×844 y 320 px, incluidos roles, menú móvil, foco y consola |
| `F2-04` | Sistema visual compartido | `F2-02` | Completado | Angular Material/CDK 22.0.6, tema Material 3, tokens y cinco estados shared; catálogo lazy `/design-system`; 6 archivos/24 pruebas, builds dentro de budget, contrastes AA calculados y QA visual aprobada por el usuario el 18 de agosto de 2026 |
| `F2-05` | OpenAPI y ambientes | `F2-03`, `F2-04` | Completado | OpenAPI regenerado; generador reproducible (`2.40.1`/`7.24.0`), cliente separado e inmutable, URL base runtime, adaptador probado y sincronización en CI; backend `verify`/SpotBugs y FrontEnd `npm ci`/`npm run check` aprobados con 8 archivos/27 pruebas y builds de 1.46 MB y 280.09 kB |
| `F2-06` | Sesión y autorización de UI | `F2-05` | Completado | sesión exclusivamente en memoria, login + `/me`, logout con limpieza garantizada, refresh single-flight y reemplazo atómico, interceptor por origen exacto y guards por rol; formato/lint, 10 archivos/49 pruebas y build de producción de 297.56 kB aprobados |
| `F2-07` | Manejo común de errores | `F2-06` | Completado | mapeo seguro por `code` para 401/403/404/409/429, validaciones por campo, `Retry-After`, correlation ID copiable, fallback no JSON y distinción API/routing; cliente generado sincronizado, `npm run check` aprobado con 13 archivos/66 pruebas y builds de 1.49 MB y 297.57 kB |
| `F2-08` | Pruebas E2E y cierre | `F2-01`–`F2-07` | Completado | Playwright 1.62.1/Chromium fijados; 66 pruebas unitarias/de componente y 14 E2E aprobadas sin reintentos; formato, lint, instalación limpia y build de producción de 297.57 kB aprobados |

Actualiza `Estado` a `En curso`, `Bloqueado` o `Completado` conforme avance el trabajo y añade una nota breve de evidencia al cerrar cada punto. Si una comprobación no puede ejecutarse, el punto permanece `Bloqueado` o `En curso` hasta registrar la causa y completar la verificación.

##### F2-01 — Crear `frontend/` con Angular, routing, estilos y pruebas

**Qué resuelve.** Establece una base reproducible sobre la que se construirán las demás entregas. Aquí se fijan las versiones compatibles de Angular 22.x, Node.js, TypeScript y npm; se crea el proyecto standalone en `frontend/`; se habilita routing; se añaden estilos globales mínimos y queda operativo el ejecutor de pruebas. No incluye todavía autenticación real ni pantallas de los módulos de negocio.

**Trabajo esperado.** Codex debe comprobar primero las versiones disponibles y la matriz oficial de compatibilidad, inicializar el proyecto sin crear otro repositorio Git, versionar `package-lock.json`, documentar los comandos locales y dejar rutas placeholder lazy sólo cuando ayuden a probar el shell futuro. Los archivos generados, cachés y dependencias deben quedar correctamente excluidos de Git.

**Seguimiento.** Se completa cuando una instalación limpia con el lockfile puede ejecutar las pruebas base, iniciar la aplicación y producir un build optimizado. Registra las versiones elegidas y los comandos exactos en el README del FrontEnd.

**Pedido sugerido a Codex:**

> Trabaja únicamente `F2-01` del plan. Lee `AGENTS.md` y la Fase 2, verifica la compatibilidad oficial de Angular 22.x con Node.js y TypeScript, y crea `frontend/` con componentes standalone, routing, estilos y pruebas. No implementes autenticación ni features de negocio. Fija versiones y lockfile, documenta cómo instalar, probar, ejecutar y compilar, y termina ejecutando instalación limpia, pruebas y build de producción. Reporta archivos y resultados.

##### F2-02 — Activar TypeScript estricto, lint, formato y budgets

**Qué resuelve.** Convierte la base creada en F2-01 en un proyecto que falla temprano ante tipos inseguros, templates incorrectos, estilo inconsistente o crecimiento accidental del bundle. Las reglas deben ser reproducibles en local y CI, no depender de extensiones personales del editor.

**Trabajo esperado.** Codex debe activar las opciones estrictas compatibles de TypeScript y Angular templates; configurar lint y formato sin reglas contradictorias; exponer scripts estables como `lint`, comprobación de formato, `test`, `build`, `e2e` y `generate:api` cuando exista; y definir budgets iniciales medidos, suficientemente exigentes para detectar regresiones pero realistas para Angular Material. El workflow CI debe utilizar `npm ci` y el lockfile cuando se incorpore la verificación del FrontEnd.

**Seguimiento.** Se completa cuando una infracción intencional de tipo/lint sería detectada y, con el código válido, todas las comprobaciones y el build cumplen los budgets. Documenta cualquier excepción; no desactives reglas globalmente para silenciar un solo caso.

**Pedido sugerido a Codex:**

> Implementa sólo `F2-02` sobre el proyecto Angular existente. Activa TypeScript y templates estrictos, configura lint, formato, scripts reproducibles y budgets medidos para desarrollo/producción. Integra las comprobaciones del FrontEnd en CI con `npm ci`, sin alterar la verificación del backend. Añade o ajusta pruebas mínimas y ejecuta formato, lint, test y build. Explica los umbrales elegidos y cualquier excepción.

##### F2-03 — Implementar layout responsive, menú por rol, encabezado y breadcrumbs

**Qué resuelve.** Proporciona el shell común de navegación antes de construir pantallas de dominio. Debe funcionar con escritorio, tablet, móvil y teclado, y representar las capacidades de `ADMIN`, `INVENTORY_MANAGER` y `SALES` sin duplicar reglas de roles en múltiples templates.

**Trabajo esperado.** Codex debe crear `layout/` con router outlet, encabezado, navegación adaptable y breadcrumbs derivados de metadata de rutas. La definición de rutas/roles debe ser central y reutilizable por menú y guards. En esta entrega se pueden usar identidades de sesión de prueba; ocultar enlaces no se presenta como seguridad, porque la API sigue siendo la autoridad. Incluye `/forbidden` y página de ruta desconocida o deja claramente preparado su punto de integración.

**Seguimiento.** Se completa cuando cada rol de prueba ve sólo sus áreas, la ruta activa y breadcrumbs son correctos, el menú puede operarse con teclado y el layout no produce desbordamientos en anchos representativos. Añade pruebas de navegación y filtrado por rol.

**Pedido sugerido a Codex:**

> Implementa únicamente `F2-03`. Crea el shell responsive en `layout/`, encabezado, menú por rol y breadcrumbs basados en metadata de rutas. Centraliza la matriz de roles, usa datos de sesión de prueba sin persistencia y deja claro que los guards no sustituyen al backend. Cubre escritorio/móvil, teclado, foco y rutas forbidden/not-found. Añade pruebas de navegación y visibilidad por los tres roles y ejecuta las comprobaciones del FrontEnd.

##### F2-04 — Definir tema, tipografía, contraste y componentes compartidos

**Qué resuelve.** Evita que cada feature invente colores, espaciado, estados y patrones de interacción. Define un lenguaje visual accesible y reutilizable antes de comenzar productos, inventario, compras o ventas.

**Trabajo esperado.** Codex debe configurar Angular Material y un tema basado en tokens; elegir escalas coherentes de color, tipografía, espaciado y elevación; comprobar contraste; y crear únicamente componentes compartidos que ya exige el plan: carga, contenido vacío, error recuperable, confirmación y feedback de operación. `shared/` no contiene reglas globales de negocio. Los componentes deben aceptar contenido y etiquetas accesibles, responder al tamaño disponible y conservar foco correctamente.

**Seguimiento.** Se completa con una página o catálogo interno de demostración que permita revisar visualmente todos los estados en escritorio y móvil, más pruebas básicas de renderizado y accesibilidad. No se considera terminado si sólo existe un archivo de variables sin uso demostrable.

**Estado al 18 de agosto de 2026.** La implementación está disponible en `/design-system` con Angular Material/CDK `22.0.6`, tema Material 3, tokens semánticos de color, tipografía, espaciado, foco, radios y elevación, y componentes standalone para carga, contenido vacío, error recuperable, confirmación y feedback. Los pares principales de color obtuvieron contrastes entre `6.36:1` y `15.67:1`; las pruebas cubren renderizado semántico, eventos, `Escape`, trampa y restauración de foco. `npm run check` aprobó formato, lint, 6 archivos/24 pruebas y builds de desarrollo (`1.36 MB`) y producción (`258.56 kB` inicial; catálogo lazy de `146.91 kB`). El usuario aprobó la QA visual de `F2-04` el 18 de agosto de 2026, por lo que la entrega queda completada.

**Pedido sugerido a Codex:**

> Trabaja sólo `F2-04`. Define el sistema visual con Angular Material, tokens de tema, tipografía, espaciado y contraste WCAG 2.2 AA. Crea componentes shared para loading, empty state, error recuperable, confirmación y feedback, sin introducir reglas de negocio. Añade una vista de demostración y verifica teclado, foco, tamaños responsive y pruebas básicas. Ejecuta lint, tests y build y resume las decisiones visuales.

##### F2-05 — Integrar cliente OpenAPI y configuración de ambientes

**Qué resuelve.** Hace que Angular consuma el contrato real de la API sin mantener DTOs duplicados a mano y sin fijar hosts o secretos en el código. Es la frontera controlada entre backend y FrontEnd.

**Trabajo esperado.** Codex debe regenerar primero `target/openapi/inventory-api-v1.json`, seleccionar y fijar una versión del generador TypeScript compatible, crear `generate:api` y separar el código generado de adaptadores escritos a mano. El resultado generado no se edita. La URL base se obtiene de configuración de ambiente o runtime; local debe poder apuntar a la API sin introducir un origen de producción. CI debe poder detectar que el cliente quedó desactualizado cuando cambió OpenAPI.

**Seguimiento.** Se completa cuando, desde una instalación limpia, un comando regenera el mismo cliente; Angular compila utilizándolo; una prueba consume al menos una operación segura del cliente; y no existen DTOs duplicados, secretos ni hosts rígidos. Cualquier deficiencia del contrato se corrige en backend y se regenera, no se parchea en el archivo generado.

**Estado al 18 de agosto de 2026.** El contrato se regeneró mediante `OpenApiContractIntegrationTest` y se corrigió su media type JSON en la configuración SpringDoc, con una aserción de regresión. `@openapitools/openapi-generator-cli` `2.40.1` fija OpenAPI Generator `7.24.0`; `generate:api` recrea exclusivamente `core/api/generated/` y `generate:api:check` comprueba hashes antes y después de regenerar. La URL base se carga desde `public/config/runtime-config.json`, se valida como origen HTTP(S) sin credenciales ni ruta y configura el cliente generado con `withCredentials: false`. El adaptador manual reutiliza tipos generados y su integración con `HttpClient` está probada. CI regenera el contrato y rechaza desincronizaciones. Aprobaron la prueba OpenAPI, `mvn verify`, SpotBugs, `npm ci`, la regeneración, la comprobación de sincronización y `npm run check` con 8 archivos/27 pruebas y builds de desarrollo (`1.46 MB`) y producción (`280.09 kB`).

**Pedido sugerido a Codex:**

> Implementa sólo `F2-05`. Regenera el OpenAPI del backend, configura un generador TypeScript con versión fija y crea `generate:api`. Mantén separado e inmutable el código generado, usa configuración de runtime/ambiente para la URL base y no incluyas secretos ni host de producción. Integra el cliente con `HttpClient`, añade una prueba de integración del adaptador y una comprobación de sincronización en CI. Ejecuta verificaciones de backend relacionadas, generación, tests y build Angular.

##### F2-06 — Implementar sesión en memoria, login, refresh, logout, `/me`, guards e interceptor

**Qué resuelve.** Implementa la decisión SEC-02 y constituye el núcleo de seguridad del MVP. Es el punto de mayor riesgo de la fase: una solución que autentica pero persiste tokens o duplica refresh no es aceptable.

**Trabajo esperado.** Codex debe crear un único servicio propietario del access y refresh token, exclusivamente en memoria; formulario de login; carga de `/me`; logout que limpia siempre; interceptor que adjunta Bearer sólo al origen exacto configurado; coordinador single-flight para que varios `401` produzcan un solo refresh; reemplazo atómico del par; exclusión de login/refresh/logout; y guards basados en la matriz central de roles. Un `401` del refresh limpia sesión y vuelve al login sin ciclos. No se usa Web Storage, IndexedDB, Cache API, cookies, service workers, estado persistido ni `withCredentials`.

**Seguimiento.** Se completa con pruebas que inspeccionan tanto el comportamiento positivo como la ausencia de persistencia. Recargar la página debe exigir login; varias solicitudes fallidas deben compartir una renovación; un refresh fallido debe liberar las solicitudes pendientes y limpiar la sesión; y el token nunca debe enviarse a otro origen. Revisa explícitamente búsquedas de APIs de almacenamiento y apariciones de tokens en logs/errores.

**Pedido sugerido a Codex:**

> Implementa exclusivamente `F2-06` siguiendo `docs/decisions/SEC-02-browser-refresh-token.md`. Crea sesión sólo en memoria, login, `/me`, refresh single-flight, reemplazo atómico, logout, guards por rol e interceptor limitado al origen exacto de la API. Prohíbe toda persistencia y evita ciclos en endpoints de auth. Añade pruebas para concurrencia, fallo de refresh, limpieza, recarga, destino externo y roles. Ejecuta una búsqueda explícita de almacenamiento/logs inseguros, además de lint, tests y build.

##### F2-07 — Implementar errores HTTP y correlation ID visible

**Qué resuelve.** Unifica cómo el usuario entiende y recupera fallos de autenticación, autorización, navegación, conflictos de negocio y rate limiting. También entrega a soporte el identificador necesario para investigar sin exponer información sensible.

**Trabajo esperado.** Codex debe mapear `ApiError.code` y `validationErrors`, sin depender del texto variable de `message`; diferenciar `401`, `403`, `404` de API y ruta Angular desconocida; mostrar conflictos `409` de forma accionable; respetar `Retry-After` en `429`; y presentar `X-Correlation-ID`/`correlationId` en el estado de error copiable. Las notificaciones deben ser accesibles y no incluir tokens, cuerpos sensibles o detalles internos. No se reintentan mutaciones automáticamente salvo idempotencia documentada.

**Seguimiento.** Se completa cuando cada estado tiene prueba de transformación y presentación, el `429` bloquea temporalmente la acción por el tiempo indicado, los errores de campo se asocian al control correcto y el correlation ID coincide con la respuesta. Prueba además respuestas incompletas o no JSON para garantizar un fallback seguro.

**Pedido sugerido a Codex:**

> Implementa sólo `F2-07`. Crea el manejo común de `ApiError` para 401/403/404/409/429, validaciones por campo, `Retry-After` y correlation ID visible/copiable para soporte. No analices textos variables ni muestres datos sensibles; diferencia errores API de rutas Angular y evita reintentos inseguros. Añade pruebas para cada estado, respuestas incompletas y accesibilidad de los mensajes. Ejecuta todas las comprobaciones del FrontEnd.

##### F2-08 — Añadir pruebas unitarias y E2E de sesión

**Qué resuelve.** Demuestra que las siete entregas anteriores funcionan juntas y protege los controles de sesión ante regresiones. No reemplaza las pruebas escritas durante cada punto; completa la cobertura transversal y los escenarios reales en navegador.

**Trabajo esperado.** Codex debe consolidar pruebas unitarias/de componente y configurar una herramienta E2E con versión fija y scripts reproducibles. Los escenarios mínimos son: visitante redirigido a login; login válido; menú y rutas por cada rol; recarga que exige autenticar de nuevo; una sola renovación ante varios `401`; refresh rechazado; logout con fallo de red que aun así limpia memoria; `403`; `409`; `429` con espera; correlation ID; navegación por teclado y viewport móvil. Los datos y credenciales usados son exclusivos de prueba y nunca se versionan como secretos.

**Seguimiento.** Se completa cuando una instalación limpia ejecuta formato, lint, unit/component tests, E2E y build de producción; no hay pruebas inestables aceptadas; y el resultado se registra en la tabla. Si E2E necesita una API real, documenta el arranque y los datos reproducibles; si usa interceptación de red para casos difíciles, conserva al menos una prueba controlada de integración con la API real.

**Pedido sugerido a Codex:**

> Cierra la Fase 2 con `F2-08`. Audita primero la evidencia de `F2-01` a `F2-07`, completa pruebas unitarias/de componente y configura E2E reproducible. Cubre login, roles, recarga sin persistencia, refresh concurrente y fallido, logout degradado, 403/409/429, correlation ID, teclado y móvil. Ejecuta instalación limpia, formato, lint, todas las pruebas y build de producción. No marques la fase completa si queda una verificación pendiente; entrega una matriz de escenarios y resultados.

**Criterio de salida**

La aplicación compila en producción, autentica de forma segura y protege navegación/acciones por rol.

---

### Fase 3 — Productos, almacenes e inventario

**Duración:** 8–12 días.  
**Dependencia:** Fase 2.

**Actividades**

- CRUD y búsqueda de productos.
- Catálogo de almacenes.
- Existencias físicas, reservadas y disponibles globales/por almacén.
- Configuración de mínimos y activación por almacén.
- Ajustes manuales con referencia y confirmación.
- Alertas y Kardex con filtros.
- Manejo de baja de producto bloqueada por documentos/saldos.
- Pruebas de permisos, concurrencia y doble envío.

#### Cómo empezar y dar seguimiento con Codex

El 23 de agosto de 2026 la Fase 2 está completada y verificada. El FrontEnd ya dispone de sesión en memoria, navegación por roles, sistema visual, manejo común de errores, cliente OpenAPI generado y pruebas E2E. **F3-01, F3-02, F3-03 y F3-04 están completadas** con features lazy para productos, almacenes, saldos y configuración de inventario, adaptadores manuales sobre el cliente generado, paginación remota, permisos y pruebas unitarias/de componente y E2E. Los siguientes cortes disponibles son F3-05 y F3-06.

Solicita a Codex una entrega por vez. Antes de editar debe leer `AGENTS.md`, el `AGENTS.md` del cliente API cuando corresponda, esta fase, el README y los controllers/DTOs/pruebas del dominio. Cada pedido debe conservar el contrato existente, indicar roles y estados de UI, exigir pruebas y terminar con evidencia. No se actualizarán dependencias ni el backend durante esta fase salvo que una brecha del contrato se demuestre y se documente antes de modificar ambos lados.

Secuencia recomendada:

```text
Fase 2 ✓ ─┬→ F3-01 ✓┐
          └→ F3-02 ✓┴→ F3-03 ✓┬→ F3-04 ✓┐
                               ├→ F3-05 ─┼→ F3-08
                               └→ F3-06 ─┤
F3-01 + F3-03 ─────────────────→ F3-07 ─┘
```

F3-01 y F3-02 son independientes después de la Fase 2. F3-04, F3-05 y F3-06 parten de la vista multi-almacén de F3-03. Las pruebas se escriben durante cada entrega; F3-08 agrega los escenarios transversales y decide el cierre de la fase.

| ID | Entrega | Dependencia | Estado | Evidencia mínima para completar |
|---|---|---|---|---|
| `F3-01` | CRUD y búsqueda de productos | Fase 2 | Completado | rutas lazy y adaptador CRUD; filtros/paginación remotos; `minimumStock` sólo en alta; roles probados; 83 unit/component y 18 E2E aprobadas; cliente sincronizado y `npm run check` en verde |
| `F3-02` | Catálogo de almacenes | Fase 2 | Completado | rutas lazy y adaptador CRUD; paginación remota sólo con `page`/`size`; roles, `404`, código duplicado, `409`, doble envío y responsive probados; 101 unit/component y 23 E2E aprobadas; cliente sincronizado y `npm run check` en verde |
| `F3-03` | Existencias globales/por almacén | `F3-01`, `F3-02` | Completado | rutas lazy MAIN/almacén; composición por `productId` con dos llamadas por página y sin N+1; aislamiento, cero/null, obsolescencia, roles y responsive probados; 111 unit/component y 29 E2E aprobadas; cliente sincronizado y `npm run check` en verde |
| `F3-04` | Mínimos y activación por almacén | `F3-03` | Completado | lectura paginada para los tres roles y edición para gestores; estado global/local separado sin N+1; mínimo, `204` + recarga, `409`, doble envío, aislamiento y accesibilidad probados; 121 unit/component y 35 E2E aprobadas; cliente sincronizado y `npm run check` en verde |
| `F3-05` | Ajustes manuales | `F3-03` | Pendiente | confirmación, una sola petición, reconciliación y rechazos probados |
| `F3-06` | Alertas y Kardex | `F3-03`, `F3-04` | Pendiente | filtros remotos, trazabilidad completa, permisos y pruebas aprobadas |
| `F3-07` | Baja de producto protegida | `F3-01`, `F3-03`, `F3-05` | Pendiente | baja exitosa y bloqueos por saldo/reserva/documento representados sin perder estado |
| `F3-08` | Permisos, concurrencia y doble envío | `F3-01`–`F3-07` | Pendiente | matriz transversal unit/component/E2E y `npm run check` en verde |

Actualiza `Estado` a `En curso`, `Bloqueado` o `Completado` y reemplaza la evidencia mínima por los resultados reales al cerrar cada entrega. Si falta una comprobación, existe una prueba inestable o el contrato no alcanza para implementar el comportamiento sin llamadas por fila, la entrega no está completa: documenta el bloqueo antes de proponer un cambio de API.

##### F3-01 — CRUD y búsqueda de productos

**Qué resuelve.** Sustituye el placeholder de `/products` por la primera feature de negocio completa y establece el patrón que reutilizarán los demás catálogos. Debe cubrir listado, detalle, alta, edición y baja exitosa, dejando para F3-07 la experiencia reforzada de bloqueos. Todos los roles consultan; sólo `ADMIN` e `INVENTORY_MANAGER` modifican.

**Trabajo esperado.** Codex debe crear `features/products/` con rutas lazy para `/products`, `/products/new`, `/products/:id` y `/products/:id/edit`; ampliar el adaptador manual sin tocar `generated/`; e implementar paginación del servidor y filtros combinables `sku`, `name` y `active`. Los filtros deben cancelar respuestas obsoletas, conservar un estado navegable y no descargar todo el catálogo. El formulario usa los tipos generados y refleja las validaciones reales: SKU 64, nombre 160, descripción 1000, precio no negativo con dos decimales y `minimumStock` sólo al crear para inicializar `MAIN`; editar el producto nunca cambia settings de almacén. Incluye carga, vacío, error, validación, `409`, éxito y prevención de doble envío.

**Seguimiento.** Se completa cuando los tres roles pueden consultar y los gestores completan el flujo feliz de crear, editar y dar de baja un producto sin saldo; `SALES` no ve ni alcanza acciones de escritura; volver desde detalle conserva filtros/página; y pruebas de adaptador, componentes, rutas y E2E cubren búsqueda, paginación, validación, SKU duplicado y doble envío. La baja bloqueada se registra como escenario pendiente de F3-07.

**Pedido sugerido a Codex:**

> Implementa únicamente `F3-01`. Lee `AGENTS.md`, la Fase 3 y el contrato real de productos. Sustituye el placeholder por una feature lazy con listado, detalle, alta, edición y baja feliz; amplía el adaptador manual usando sólo tipos generados. Implementa filtros `sku`/`name`/`active`, paginación remota, estados completos y acciones sólo para `ADMIN`/`INVENTORY_MANAGER`. Respeta que `minimumStock` sólo se envía al crear para `MAIN`. No cambies backend ni dependencias. Añade pruebas unitarias/de componente y E2E, ejecuta `npm run generate:api:check` y `npm run check`, y reporta evidencia.

##### F3-02 — Catálogo de almacenes

**Qué resuelve.** Introduce el selector y directorio de ubicaciones del que dependen las vistas multi-almacén. La API sólo ofrece paginación para este catálogo; no debe simularse una búsqueda descargando páginas completas.

**Trabajo esperado.** Codex debe crear `features/warehouses/`, reemplazar `/warehouses` y añadir el detalle o panel necesario para crear, editar y desactivar. Reutiliza `WarehousesService` mediante un adaptador manual, con `page` y `size` del servidor. El formulario refleja código máximo 32, nombre 160, descripción 1000 y estado activo; el servidor normaliza el código. Todos los roles consultan, mientras que sólo gestores modifican. La desactivación exige confirmación y puede recibir `409` si hay stock, reservas u órdenes abiertas; un almacén inactivo permanece identificable y no admite nuevas operaciones.

**Seguimiento.** Se completa cuando la lista y el detalle manejan carga, vacío, error, paginación y estado activo; los gestores pueden crear/editar/desactivar sin doble envío; `SALES` queda en lectura; y las pruebas cubren código duplicado, `404`, conflicto de desactivación, permisos y navegación responsive. No agregues filtros que el endpoint no soporta.

**Evidencia de cierre (23 de agosto de 2026).** La feature lazy quedó disponible en `/warehouses`, `/warehouses/new`, `/warehouses/:id` y `/warehouses/:id/edit`, con adaptador manual y consulta limitada a `page`/`size`. `npm run generate:api:check` confirmó el cliente sincronizado; `npm run check` aprobó formato, lint, 101 pruebas unitarias/de componente, 23 E2E y builds de desarrollo y producción. La regeneración previa del OpenAPI del backend no pudo repetirse porque Docker/Testcontainers no encontró un daemon; no hubo cambios de API y la comprobación del cliente usó el contrato canónico vigente en `target/openapi/inventory-api-v1.json`.

**Pedido sugerido a Codex:**

> Implementa sólo `F3-02`. Construye el catálogo lazy de almacenes sobre el cliente generado, con adaptador manual, lista paginada, detalle, alta, edición y desactivación confirmada. No simules búsqueda porque el contrato sólo admite `page` y `size`. Aplica lectura para todos y escritura sólo para gestores; cubre estados completos, `404`, código duplicado, `409`, doble envío y accesibilidad. No cambies la API. Ejecuta sincronización del cliente, pruebas y `npm run check` y entrega resultados.

##### F3-03 — Existencias físicas, reservadas y disponibles globales/por almacén

**Qué resuelve.** Hace visible el saldo que gobierna ventas y operación. Debe evitar una ambigüedad del plan histórico: `/api/v1/inventory/**` es un alias compatible hacia el almacén determinista `MAIN`, no una suma de todos los almacenes. La vista multi-almacén usa `/api/v1/warehouses/{warehouseId}/inventory/**`.

**Trabajo esperado.** Codex debe crear `features/inventory/`, adaptadores manuales y rutas para la vista de `MAIN` y `/warehouses/:id/inventory`. Muestra `quantity`, `reservedQuantity`, `availableQuantity` y `updatedAt`; conserva `available = physical - reserved` y representa `updatedAt=null` como producto aún sin movimientos. Los nombres/SKU deben componerse por `productId` sin una petición por fila y sin suponer que dos páginas tienen el mismo orden; si el contrato actual no permite una composición correcta y eficiente, detén la entrega y documenta la brecha. Todos los roles pueden consultar. Usa paginación remota, selector de almacén y enlaces a producto/configuración según permisos.

**Seguimiento.** Se completa cuando cambiar de almacén nunca conserva saldos de la ubicación anterior, `MAIN` se etiqueta como alias de compatibilidad y no como total global, los tres tipos de existencia son comprensibles y la UI cubre cero, carga, vacío, error, recarga y paginación. Las pruebas deben demostrar aislamiento por almacén, unión por ID, cancelación de respuestas obsoletas y acceso de los tres roles.

**Evidencia de cierre (23 de agosto de 2026).** `/inventory` identifica explícitamente a `MAIN` como almacén y alias compatible, mientras `/warehouses/:id/inventory` conserva el aislamiento de la ubicación seleccionada. Cada página une `InventoryResponse` con `InventorySettingResponse` mediante `productId`, valida cardinalidad/almacén/cantidades y usa exactamente dos peticiones independientemente del número de filas. El selector pagina almacenes remotamente. `npm run generate:api:check` confirmó el cliente sincronizado y `npm run check` aprobó formato, lint, 111 pruebas unitarias/de componente, 29 E2E y builds de desarrollo y producción. No se modificaron backend, OpenAPI ni código generado.

**Pedido sugerido a Codex:**

> Implementa exclusivamente `F3-03`. Inspecciona los DTOs y endpoints de inventario antes de diseñar la vista. Sustituye los placeholders de inventario por saldos físicos, reservados y disponibles para `MAIN` y por almacén, con selector, paginación remota y composición de producto por ID sin N+1. Deja explícito que `/api/v1/inventory` apunta a `MAIN`, no al total multi-almacén. Si falta contrato para una unión correcta, documenta el bloqueo antes de cambiar backend. Añade pruebas de aislamiento, cero/null, respuestas obsoletas, roles y estados; ejecuta las comprobaciones completas.

##### F3-04 — Configuración de mínimos y activación por almacén

**Qué resuelve.** Permite decidir qué productos operan en cada almacén y cuándo generan alerta. La activación del catálogo de producto y la activación dentro de un almacén son conceptos diferentes y nunca deben fusionarse en un solo control.

**Trabajo esperado.** Codex debe implementar `/warehouses/:id/settings` usando las rutas `settings` del almacén. Todos los roles leen `sku`, `name`, `minimumStock` y estado por almacén; sólo gestores actualizan `minimumStock >= 0` y `active`. La respuesta de escritura es `204`, por lo que la fila se vuelve a consultar antes de mostrar el dato como reconciliado. Desactivar una configuración puede responder `409` si conserva stock o reservas. Cambiar el catálogo con `PUT /products/{id}` no modifica settings y la UI no debe insinuar lo contrario.

**Seguimiento.** Se completa cuando se distingue visual y semánticamente producto global inactivo de producto inactivo en un almacén, los gestores actualizan una fila sin afectar otras ubicaciones, `SALES` sólo consulta y un conflicto conserva el formulario con una explicación accionable y correlation ID. Prueba mínimo negativo/cero, `204`, recarga, permisos, doble envío y aislamiento entre almacenes.

**Evidencia de cierre (23 de agosto de 2026).** `/warehouses/:id/settings` lista settings paginados y valida que todas las filas pertenezcan al almacén solicitado. Como `InventorySettingResponse` no contiene el estado global, éste se obtiene con una única consulta al producto al abrir la fila y se muestra separado y de sólo lectura, sin llamadas por fila en el listado. La escritura queda reservada a gestores y encadena `PUT 204` con el `GET` individual antes de reconciliar. Los conflictos por desactivación conservan valores y correlation ID. `npm run generate:api:check` confirmó el cliente sincronizado y `npm run check` aprobó formato, lint, 121 pruebas unitarias/de componente, 35 E2E y builds de desarrollo y producción. No se modificaron backend, OpenAPI ni código generado.

**Pedido sugerido a Codex:**

> Implementa sólo `F3-04` en `/warehouses/:id/settings`. Usa el cliente generado mediante adaptador manual, separa estado global del producto y activación por almacén, valida mínimo no negativo y permite escritura únicamente a gestores. Tras el `204`, vuelve a consultar y reconcilia; maneja `409` por stock/reservas sin perder el formulario. Cubre carga, vacío, error, permisos, doble envío, aislamiento y accesibilidad. No edites generado ni cambies backend. Ejecuta pruebas y `npm run check`.

##### F3-05 — Ajustes manuales con referencia y confirmación

**Qué resuelve.** Permite entradas o salidas extraordinarias manteniendo trazabilidad y evitando que una interacción duplicada altere dos veces el inventario. Es una mutación no idempotente: nunca se reintenta automáticamente.

**Trabajo esperado.** Codex debe añadir la acción a la vista del almacén y preferir la ruta explícita `/warehouses/{warehouseId}/inventory/{productId}/adjustments`; el alias `/inventory/{productId}/adjustments` se reserva para `MAIN`. Sólo gestores acceden. El formulario puede presentar Entrada/Salida, pero serializa un `quantityDelta` entero distinto de cero y una `reference` opcional de hasta 128 caracteres. Antes de enviar muestra producto, almacén, saldo físico/reservado/disponible, delta y resultado previsto; bloquea el botón mientras hay petición. La respuesta del servidor, no el cálculo optimista, sustituye el saldo visible. Un `400` por stock reservado o saldo negativo conserva contexto y no deja cambios parciales.

**Seguimiento.** Se completa cuando una confirmación produce exactamente una petición, cancelar no produce ninguna, cambiar de almacén invalida el diálogo abierto y la respuesta reconciliada actualiza saldo y vistas dependientes. Prueba entradas, salidas, cero, referencia límite, saldo insuficiente/reservado, `401`/`403`/`429`, doble clic, fallo de red incierto y ausencia de reintento automático.

**Pedido sugerido a Codex:**

> Implementa únicamente `F3-05`. Añade ajustes manuales por almacén para gestores, con entrada/salida traducida a delta firmado no cero, referencia opcional, resumen de confirmación y prevención estricta de doble envío. Usa la ruta del almacén, salvo `MAIN` cuando corresponda, y reconcilia sólo con la respuesta API. No reintentes la mutación. Cubre stock insuficiente o reservado, cambio de almacén, fallo de red, 401/403/429 y accesibilidad. Añade pruebas unitarias/de componente y E2E y ejecuta `npm run check`.

##### F3-06 — Alertas y Kardex con filtros

**Qué resuelve.** Entrega dos vistas operativas para gestores: alertas accionables de reposición y trazabilidad histórica de todos los cambios físicos y reservados. `SALES` no puede consultar estos endpoints.

**Trabajo esperado.** Codex debe implementar alertas para `MAIN` y por almacén con `search`, `outOfStockOnly`, `page` y `size`, mostrando mínimo, disponible, reposición sugerida y `LOW_STOCK`/`OUT_OF_STOCK`. El Kardex usa filtros remotos combinables `productId`, `type`, fechas ISO inclusivas y referencia exacta; valida que `from <= to`; mantiene paginación estable y representa por separado `quantityDelta`/saldos físicos y `reservationDelta`/saldos reservados. No inventa nombres para productos eliminados: si ya no pueden consultarse, muestra un identificador seguro y conserva el historial. Evita N+1 y respuestas obsoletas.

**Seguimiento.** Se completa cuando filtros, URL y paginación son reproducibles; cambiar almacén limpia resultados incompatibles; las alertas coinciden con el mínimo configurado; y cada movimiento muestra tipo, antes/después, referencia, fecha, actor y almacén. Prueba todos los filtros, rango inválido, producto eliminado, vacíos, permisos, teclado y viewport móvil.

**Pedido sugerido a Codex:**

> Implementa sólo `F3-06`. Crea alertas y Kardex lazy para `MAIN` y cada almacén usando filtros/paginación del servidor. Restringe ambos a `ADMIN` e `INVENTORY_MANAGER`; representa alertas, reposición y efectos físicos/reservados sin N+1. Valida fechas, conserva filtros en URL, cancela respuestas obsoletas y admite historial de productos eliminados con fallback por ID. Añade pruebas de filtros, permisos, vacío/error, responsive y accesibilidad y ejecuta sincronización del cliente y `npm run check`.

##### F3-07 — Manejo de baja de producto bloqueada por documentos o saldos

**Qué resuelve.** Completa el ciclo de vida iniciado en F3-01 y evita confundir suspensión reversible (`active=false`) con baja lógica terminal. Una baja sólo es válida sin stock físico, reservas ni documentos pendientes; el SKU continúa reservado y el Kardex histórico permanece.

**Trabajo esperado.** Codex debe mostrar una confirmación reforzada que explique que la baja es terminal para la operación, identificar el producto y no sugerir que libera el SKU. Antes de confirmar puede mostrar los saldos ya cargados, pero no decide localmente si la baja es válida: la API conserva la autoridad. En éxito `204`, refresca catálogo y navegación. En `409`, mantiene el producto visible, muestra los posibles bloqueos conocidos —saldo, reserva u operación pendiente— sin afirmar cuál fue si el código no lo distingue, conserva correlation ID y ofrece rutas seguras para revisar inventario/Kardex. Nunca analiza el texto variable del backend para tomar decisiones.

**Seguimiento.** Se completa con baja exitosa de producto sin dependencias y conflictos representados para existencia, reserva y documento pendiente. Las pruebas verifican que `active=false` sigue visible/editable según rol, que una baja exitosa desaparece de vistas operativas sin perder el acceso histórico permitido, que un conflicto no elimina la fila y que clics repetidos producen una sola petición.

**Pedido sugerido a Codex:**

> Implementa exclusivamente `F3-07`. Refuerza la baja lógica de productos sin confundirla con `active=false`. La API decide si hay stock, reservas o documentos pendientes; en `409` no analices mensajes ni afirmes un bloqueo específico, conserva el producto y muestra correlation ID y enlaces de revisión. En `204`, reconcilia catálogo sin perder trazabilidad. Prueba éxito, los tres tipos de bloqueo, producto suspendido, permisos y doble clic. No cambies el contrato salvo brecha demostrada y ejecuta todas las comprobaciones.

##### F3-08 — Pruebas de permisos, concurrencia y doble envío

**Qué resuelve.** Verifica en conjunto que los siete puntos anteriores respetan roles, aislamiento por almacén e integridad ante respuestas concurrentes. La UI reduce errores de operación, pero no reemplaza los locks y transacciones ya probados en PostgreSQL.

**Trabajo esperado.** Codex debe consolidar una matriz unit/component/E2E: los tres roles consultan catálogo y saldos; `SALES` no puede mutar ni abrir alertas/Kardex; los gestores sí; respuestas antiguas de filtros o almacenes no sobrescriben las nuevas; crear, editar, configurar, ajustar y dar de baja bloquean doble envío; un fallo o timeout no se convierte en reintento silencioso; y cada mutación vuelve a conciliar con API. Incluye navegación por teclado, foco tras diálogos, móvil y los estados comunes. Para concurrencia de inventario real, conserva y ejecuta las pruebas backend/Testcontainers relevantes en vez de simular garantías transaccionales sólo en el navegador.

**Seguimiento.** Se completa cuando la matriz de aceptación registra cada rol y operación, no hay pruebas omitidas o inestables, el cliente generado permanece sincronizado y pasan `npm ci`, `npm run generate:api:check`, `npm run check`, las pruebas backend de productos/inventario/almacenes y SpotBugs. Registra conteos, builds, budgets y cualquier QA visual manual; sólo entonces marca la Fase 3 completa.

**Pedido sugerido a Codex:**

> Cierra la Fase 3 con `F3-08`. Audita primero la evidencia de `F3-01` a `F3-07` y crea una matriz transversal de roles, aislamiento por almacén, respuestas concurrentes, doble envío y reconciliación. Completa unit/component/E2E y ejecuta instalación limpia, sincronización OpenAPI, `npm run check`, pruebas backend relevantes con Testcontainers y SpotBugs. Incluye teclado, móvil y QA visual. No declares la fase completa si queda una prueba omitida, inestable o una verificación pendiente; reporta comandos, resultados, conteos y budgets.

**Criterio de salida**

Un responsable administra productos y existencias multi-almacén sin utilizar Swagger.

---

### Fase 4 — Proveedores, compras y recepciones

**Duración:** 7–11 días.  
**Dependencia:** Fases 2 y 3.

**Actividades**

- CRUD y filtros de proveedores.
- Asociaciones proveedor-producto, SKU y último costo.
- Captura y edición de órdenes `DRAFT`.
- Emisión, cancelación e historial.
- Recepciones parciales/totales con referencia externa.
- Tratamiento visual de reintento idempotente: `201` nuevo y `200` ya existente.
- Validación de pendientes, costos, cantidades y almacén destino.

**Criterio de salida**

Una recepción actualiza stock una sola vez y puede conciliarse con orden, proveedor, almacén y movimiento.

---

### Fase 5 — Clientes y pedidos

**Duración:** 7–11 días.  
**Dependencia:** Fases 2 y 3.

**Actividades**

- CRUD, búsqueda y reactivación de clientes.
- Captura de pedidos con cliente, almacén y productos.
- Estados `PENDING`, `RESERVED`, `CONFIRMED`, `CANCELLED`.
- Reserva, liberación, confirmación, cancelación y edición controlada.
- Precios históricos, subtotales y total.
- Pruebas de stock insuficiente y rollback multiartículo.

**Criterio de salida**

`SALES` completa todo el ciclo autorizado y el inventario concilia con cada transición.

---

### Fase 6 — Transferencias y conteos físicos

**Duración:** 7–11 días.  
**Dependencia:** Fase 3.

**Actividades**

- Listado, borrador y edición de transferencias.
- Despacho, unidades en tránsito, recepción y cancelación.
- Conteos completos/selectivos.
- Captura rápida por producto, validación de pendientes y diferencias.
- Envío, publicación idempotente y cancelación.
- Confirmaciones reforzadas y visualización de movimientos resultantes.

**Criterio de salida**

Transferencias y conteos no duplican ni pierden unidades ante reintentos y reflejan trazabilidad completa.

---

### Fase 7 — Usuarios y perfil

**Duración:** 4–6 días.  
**Dependencia:** Fase 2.

**Actividades**

- Listado/filtros y alta de usuarios.
- Estado, bloqueo y roles.
- Restablecimiento administrativo de contraseña.
- Revocación de sesiones.
- Perfil y cambio de contraseña propia.
- Conflicto del último administrador activo.

**Criterio de salida**

Los cambios sensibles actualizan la interfaz y revocan las sesiones anteriores inmediatamente.

---

### Fase 8 — Dashboard, reportes y observabilidad

**Duración:** 5–9 días.  
**Dependencia:** Fases 3–7.

**Backend**

- endpoint agregado de dashboard por rol;
- reportes/exportaciones aprobados;
- Spring Boot Actuator con endpoints mínimos protegidos;
- liveness/readiness, métricas y logs estructurados;
- monitoreo de rate limit, fallos de autenticación y operaciones rechazadas.

**FrontEnd**

- KPIs, alertas, documentos por estado y actividad reciente;
- exportaciones con filtros;
- vista de estado solo para administradores si aplica.

**Criterio de salida**

La operación se supervisa sin descargar conjuntos completos al navegador y existen alertas accionables.

---

### Fase 9 — Seguridad, accesibilidad y rendimiento

**Duración:** 5–8 días.  
**Dependencia:** funciones objetivo terminadas.

**Actividades**

- pruebas de autorización horizontal/vertical;
- sesión expirada, refresh reuse, rate limit y revocación;
- XSS, CSP, Trusted Types y ausencia de datos sensibles en almacenamiento/logs;
- teclado, foco, contraste, etiquetas y lector de pantalla;
- rendimiento de tablas, consultas, bundles y renderizado;
- escaneo de dependencias Angular y generación de SBOM;
- pruebas E2E de todos los casos críticos;
- runbook de rotación de claves JWT.

**Criterio de salida**

Cero hallazgos críticos/altos sin excepción aprobada, cero defectos P0/P1 y evidencia automatizada de flujos críticos.

---

### Fase 10 — Despliegue, aceptación y estabilización

**Duración:** 4–8 días más observación.  
**Dependencia:** Fase 9.

**Actividades**

- artefactos reproducibles de API y Angular;
- HTTPS, CSP, CORS, caché y secretos por ambiente;
- migraciones controladas y respaldo/restauración probada;
- monitoreo y alertas;
- pruebas de aceptación por rol;
- capacitación y conciliación inicial.

**Criterio de salida**

La solución está desplegada, monitoreada y respaldada; los responsables aprueban los escenarios y el inventario concilia.

## 10. Dependencias y paralelización

```text
Fase 0 ✓ → Fase 1 ✓
                    └─→ Fase 2 (completada: F2-01–F2-08)
                         ├─→ Fase 3 ─┬→ Fase 4
                         │           ├→ Fase 5
                         │           └→ Fase 6
                         └─→ Fase 7

Fases 4, 5, 6 y 7 pueden ejecutarse en paralelo después de sus dependencias.
Todas convergen en Fase 8 → Fase 9 → Fase 10.
```

## 11. Backlog priorizado

### P0 — Completado antes de la sesión Angular

- SEC-01: parser literal de IP sin DNS y pruebas.
- SEC-02: tokens sólo en memoria para el MVP.
- Java 25 reproducible en el proyecto.
- CORS local para Angular CLI.
- exclusiones preventivas de secretos.
- OpenAPI reproducible y CI verde.

`F2-01`–`F2-08` están completadas. El cierre de `F2-03` incluyó 17 pruebas, builds sin advertencias de budget y revisión visual en escritorio y móvil; el único solapamiento detectado a 320 px se corrigió preservando el nombre visible y accesible de la aplicación. La tabla README de proveedores se completó el mismo día. `F2-04` obtuvo aprobación de QA visual el 18 de agosto de 2026. `F2-05` dejó el contrato y el cliente TypeScript regenerables y sincronizados en CI, configuración runtime sin secretos, 27 pruebas de FrontEnd aprobadas y verificaciones completas de backend. `F2-06` implementó la sesión no persistente de SEC-02 con 49 pruebas aprobadas, incluida concurrencia de refresh, fallo, limpieza, recarga, destinos externos y roles. `F2-07` añadió mapeo y presentación común segura de errores con 66 pruebas aprobadas, incluida validación, rate limit, referencia de soporte y fallbacks. `F2-08` fijó Playwright/Chromium, añadió 14 E2E sin reintentos para los flujos críticos, corrigió el foco post-render del menú móvil detectado en Chromium y aprobó una instalación limpia con todas las comprobaciones del FrontEnd.

### P1 — MVP FrontEnd

- sesión, layout y permisos;
- productos, almacenes e inventario;
- proveedores y compras;
- clientes y pedidos;
- transferencias y conteos;
- usuarios y perfil;
- pruebas E2E y accesibilidad.

### P2 — Madurez operativa

- dashboard agregado y reportes;
- observabilidad/health checks;
- auditoría general de cambios;
- rotación de claves;
- SBOM y escaneo completo periódico;
- importación masiva, códigos de barras e imágenes si el negocio lo aprueba.

## 12. Escenarios de aceptación esenciales

1. Un visitante no accede a rutas protegidas.
2. Cada rol ve únicamente sus módulos y la API rechaza acciones no autorizadas.
3. Varias respuestas `401` simultáneas disparan una sola renovación.
4. Un `429` deshabilita temporalmente la acción según `Retry-After`.
5. Tokens nunca aparecen en URL, logs, mensajes ni herramientas de analítica.
6. Una entrada/salida afecta únicamente el almacén elegido.
7. Una salida superior al disponible se rechaza sin cambio parcial.
8. Una recepción repetida con la misma referencia no duplica stock.
9. Una transferencia conserva origen + tránsito + destino.
10. Un conteo publicado dos veces no duplica ajustes.
11. Reservar un pedido reduce disponible, no físico.
12. Confirmar/cancelar un pedido afecta stock exactamente una vez.
13. Un fallo multiartículo revierte la transacción completa.
14. `SALES` no ajusta inventario ni administra compras/usuarios.
15. `INVENTORY_MANAGER` no administra clientes/pedidos/usuarios.
16. No se puede eliminar un producto/almacén con saldos o documentos pendientes.
17. No se puede deshabilitar al último administrador activo.
18. Cambiar contraseña, roles o estado invalida sesiones anteriores.
19. Todas las tablas manejan carga, vacío, error y paginación.
20. Los flujos principales son operables con teclado y mensajes accesibles.

## 13. Definition of Done

Una funcionalidad se considera terminada cuando:

- regla de negocio y permisos están documentados;
- OpenAPI y cliente TypeScript están actualizados;
- migración Flyway existe cuando cambia el esquema;
- API valida, autoriza y devuelve código/correlation ID;
- UI cubre carga, vacío, error, conflicto, rate limit y éxito;
- existe prevención de doble envío;
- pruebas unitarias, integración/componente y E2E relevantes pasan;
- se verificaron responsive y WCAG básica;
- tokens/secretos no se guardan ni registran de forma insegura;
- Maven, Angular, SpotBugs y Trivy pasan en CI;
- documentación de usuario/operación está actualizada.

## 14. Riesgos restantes

| Riesgo | Impacto | Mitigación |
|---|---|---|
| XSS durante una sesión activa | lectura de tokens presentes en memoria | CSP estricta, Angular sin bypass inseguros, sanitización y dependencias controladas |
| proxy mal configurado | evasión de rate limit/IP falsa | lista mínima de proxies, sobrescribir XFF y límite perimetral |
| ruta local rígida de JDK en PowerShell | build falla en otra máquina | parametrizar/detectar un JDK 25 manteniendo validación estricta |
| sesión no persistente | recarga obliga a iniciar sesión | comunicar UX; BFF/cookie segura sólo mediante nueva decisión |
| sin observabilidad | incidentes detectados tarde | ejecutar Fase 8 antes de producción |
| rotación JWT sin interrupciones no disponible | corte coordinado e invalidación de tokens | ensayar runbook y luego implementar `kid`/JWKS o estrategia equivalente |
| dashboard/reportes sin endpoints agregados | llamadas múltiples o carga innecesaria | medir el MVP y añadir consultas agregadas sólo donde aporten valor |
| acciones multiartículo desde UI | doble envío o incertidumbre | idempotencia backend + bloqueo visual + reconciliación |
| OneDrive | bloqueos o sincronización parcial | disponibilidad offline y no ejecutar dos copias simultáneas |

## 15. Referencias

- [Compatibilidad oficial de Angular](https://angular.dev/reference/versions)
- [Guía de estilo de Angular](https://angular.dev/style-guide)
- [Seguridad en Angular](https://angular.dev/best-practices/security)
- [Interceptors de HttpClient](https://angular.dev/guide/http/interceptors)
- [Routing en Angular](https://angular.dev/guide/routing)
- [Instrucciones de proyecto para Codex con AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [Cómo formular y verificar tareas con Codex](https://learn.chatgpt.com/docs/prompting#prompting-codex)
- [Decisión SEC-02: sesión del navegador](docs/decisions/SEC-02-browser-refresh-token.md)
- [Runbook de rotación JWT](docs/runbooks/jwt-key-rotation.md)
- [CI #20 del commit auditado](https://github.com/BrandonLC16/Inventario/actions/runs/32044666247)
