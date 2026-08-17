# Plan de trabajo actualizado: FrontEnd Angular para Inventory API

**Fecha de revisión:** 17 de agosto de 2026  
**Proyecto fuente:** `C:\Proyectos\Inventario`  
**Copia de trabajo:** `C:\Users\brand\OneDrive\Documentos\Inventario`  
**Rama:** `master`  
**Commit verificado:** `15de61f8085c55c01c61f0a18f8afe405378be76`  
**Estado:** API sincronizada y funcional; FrontEnd en Fase 2, con `F2-01` y `F2-02` completadas y `F2-03` implementada a falta de validación visual en un navegador conectado.

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

El 17 de agosto de 2026 se comprobó inicialmente que `frontend/` no existía y el trabajo comenzó por **F2-01**, sin adelantar autenticación, diseño final ni módulos de negocio. `F2-01` y `F2-02` ya están completadas; `F2-03` tiene código y comprobaciones automatizadas aprobadas, pero conserva estado **En curso** hasta validar visualmente los anchos representativos en un navegador conectado. `F2-04` también está habilitada. Conviene pedir a Codex una entrega a la vez: un pedido útil identifica el comportamiento deseado, los archivos o documentos que debe consultar, las restricciones que debe conservar y las verificaciones que debe ejecutar.

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
| `F2-03` | Layout y navegación por rol | `F2-02` | En curso | shell y política central implementados; `npm ci` y `npm run check` aprobados; 4 archivos/17 pruebas; builds de 1.32 MB y 228.97 kB; validación visual pendiente por falta de navegador conectado |
| `F2-04` | Sistema visual compartido | `F2-02` | Pendiente | tema, tokens y estados comunes accesibles demostrables |
| `F2-05` | OpenAPI y ambientes | `F2-03`, `F2-04` | Pendiente | cliente regenerable y URL de API configurable sin secretos |
| `F2-06` | Sesión y autorización de UI | `F2-05` | Pendiente | login completo y pruebas de memoria/refresh/guards/interceptor |
| `F2-07` | Manejo común de errores | `F2-06` | Pendiente | comportamiento probado para 401/403/404/409/429 y correlación |
| `F2-08` | Pruebas E2E y cierre | `F2-01`–`F2-07` | Pendiente | flujos críticos E2E y todas las comprobaciones de la fase en verde |

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

**Pedido sugerido a Codex:**

> Trabaja sólo `F2-04`. Define el sistema visual con Angular Material, tokens de tema, tipografía, espaciado y contraste WCAG 2.2 AA. Crea componentes shared para loading, empty state, error recuperable, confirmación y feedback, sin introducir reglas de negocio. Añade una vista de demostración y verifica teclado, foco, tamaños responsive y pruebas básicas. Ejecuta lint, tests y build y resume las decisiones visuales.

##### F2-05 — Integrar cliente OpenAPI y configuración de ambientes

**Qué resuelve.** Hace que Angular consuma el contrato real de la API sin mantener DTOs duplicados a mano y sin fijar hosts o secretos en el código. Es la frontera controlada entre backend y FrontEnd.

**Trabajo esperado.** Codex debe regenerar primero `target/openapi/inventory-api-v1.json`, seleccionar y fijar una versión del generador TypeScript compatible, crear `generate:api` y separar el código generado de adaptadores escritos a mano. El resultado generado no se edita. La URL base se obtiene de configuración de ambiente o runtime; local debe poder apuntar a la API sin introducir un origen de producción. CI debe poder detectar que el cliente quedó desactualizado cuando cambió OpenAPI.

**Seguimiento.** Se completa cuando, desde una instalación limpia, un comando regenera el mismo cliente; Angular compila utilizándolo; una prueba consume al menos una operación segura del cliente; y no existen DTOs duplicados, secretos ni hosts rígidos. Cualquier deficiencia del contrato se corrige en backend y se regenera, no se parchea en el archivo generado.

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
                    └─→ Fase 2 (en curso: F2-03 pendiente de revisión visual; F2-04 siguiente)
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

La generación reproducible del cliente TypeScript está programada para `F2-05`, después del layout y el sistema visual. `F2-01` y `F2-02` se completaron el 17 de agosto de 2026. `F2-03` quedó implementada y con verificaciones automatizadas aprobadas ese día; falta su revisión visual en un navegador conectado antes de marcarla completada. La tabla README de proveedores se completó el mismo día.

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

