# Inventory API y FrontEnd Angular — reglas para agentes

## Alcance y objetivo

Este archivo aplica a todo el repositorio. Antes de modificar cualquier archivo, lee estas reglas y, si trabajas dentro de una carpeta que contiene otro `AGENTS.md`, aplica también sus instrucciones. Las reglas más cercanas al archivo modificado complementan o concretan estas reglas; no deben utilizarse para relajar seguridad, integridad de inventario ni calidad.

El objetivo del repositorio es mantener Inventory API y construir su cliente web en `frontend/` siguiendo `PLAN_TRABAJO_ANGULAR_INVENTORY_API.md`. Implementa únicamente el alcance solicitado y respeta las dependencias entre fases del plan. No adelantes fases completas, rediseños, actualizaciones de dependencias o cambios de contrato que no sean necesarios para la tarea actual.

## Fuentes de verdad

Consulta, en este orden y según el área afectada:

1. Este archivo y cualquier `AGENTS.md` más específico.
2. El código, las migraciones y las pruebas vigentes.
3. `target/openapi/inventory-api-v1.json`, generado por las pruebas del backend, para el contrato HTTP actual.
4. `docs/decisions/SEC-02-browser-refresh-token.md` para la sesión del navegador.
5. `README.md` para configuración, roles, comandos y operación local.
6. `PLAN_TRABAJO_ANGULAR_INVENTORY_API.md` para arquitectura, fases, rutas de UI, aceptación y Definition of Done.

El plan es una fotografía fechada y una hoja de ruta. No des por vigentes sus conteos de rutas, pruebas, migraciones o versiones si el repositorio actual demuestra otra cosa. Ante una discrepancia, verifica el código y el OpenAPI generado, conserva el comportamiento actual salvo que la tarea pida cambiarlo y actualiza la documentación afectada.

## Estado y límites del trabajo

- Inspecciona `git status` y los archivos relacionados antes de editar. Conserva cambios del usuario y archivos no relacionados; nunca los reviertas, sobrescribas ni incluyas por conveniencia.
- Determina la fase actual comprobando el árbol real. Si `frontend/` todavía no existe, la siguiente fase prevista es la Fase 2; no asumas que seguirá así en trabajos posteriores.
- Mantén los cambios pequeños, enfocados y coherentes con el estilo existente. Evita refactors, formateos masivos o cambios de nombres ajenos al objetivo.
- No cambies simultáneamente el contrato del backend y el FrontEnd para ocultar un error de integración. Explica el cambio de contrato, añade pruebas, regenera OpenAPI y actualiza el cliente.
- No edites artefactos de `target/`, dependencias instaladas, salidas de compilación ni código generado a mano. Regénéralos con el comando reproducible correspondiente.
- No introduzcas secretos, credenciales, tokens, claves privadas ni datos personales reales. Los archivos de ambiente versionados son plantillas sin secretos.
- No uses comandos destructivos ni elimines datos, migraciones, volúmenes o archivos del usuario salvo petición explícita y alcance confirmado.

## Arquitectura del repositorio

### Backend

- Conserva Java 25, Spring Boot, PostgreSQL, Flyway y el monolito modular organizado por funcionalidad bajo `com.example.inventory`.
- Los controllers traducen HTTP y delegan. Las reglas de negocio y los límites transaccionales pertenecen a services; la persistencia pertenece a repositories.
- No expongas entidades JPA. Usa DTOs de request/response, Bean Validation y el formato común `ApiError`.
- Todas las rutas de negocio permanecen versionadas bajo `/api/v1`.
- La autorización es cerrada por defecto. Toda ruta nueva debe declarar explícitamente métodos y roles en Spring Security y tener pruebas de acceso permitido y denegado; conserva `anyRequest().denyAll()`.
- Todo cambio de esquema se realiza con una migración Flyway nueva. No modifiques una migración que ya pudo ejecutarse ni dependas de `ddl-auto` para cambiar el esquema.
- Conserva las transacciones, bloqueos, orden estable de adquisición e idempotencia que protegen inventario y documentos. No simplifiques concurrencia sin pruebas equivalentes.
- No expongas detalles internos, SQL, stack traces, existencia de usuarios o credenciales en errores o logs.

### FrontEnd Angular

- El cliente vive en `frontend/` como proyecto independiente dentro del repositorio.
- Usa Angular 22.x como línea inicial del plan, componentes standalone, rutas lazy, TypeScript estricto, formularios reactivos, `HttpClient` e interceptores funcionales. Antes de crear o actualizar el proyecto, confirma la matriz oficial compatible de Angular, Node.js y TypeScript y fija versiones exactas compatibles.
- Usa el package manager indicado por el lockfile. Al inicializar el FrontEnd, usa npm de forma predeterminada, versiona `package-lock.json` y usa instalaciones reproducibles con `npm ci`.
- Mantén esta separación:

```text
frontend/src/app/
  core/       sesión, configuración, guards, HTTP y servicios singleton
  layout/     shell, navegación, encabezado y breadcrumbs
  shared/     UI reutilizable y utilidades sin reglas globales de negocio
  features/   una carpeta lazy por dominio
```

- `core` se carga una vez y no depende de `features`. `shared` no se convierte en un contenedor de estado o reglas de negocio. Cada feature agrupa sus páginas, componentes, acceso a datos y modelos propios.
- Conserva los dominios del plan: `dashboard`, `products`, `warehouses`, `inventory`, `suppliers`, `purchases`, `transfers`, `inventory-counts`, `customers`, `orders`, `users` y `profile`.
- Usa Signals para estado local y derivado de UI; usa RxJS para HTTP, cancelación, coordinación y flujos asíncronos. No añadas un store global sin una necesidad demostrada y una decisión documentada.
- Angular Material es la base visual mientras no exista otra decisión de diseño. Implementa diseño responsive y WCAG 2.2 AA: teclado, foco visible, etiquetas, mensajes accesibles, contraste y orden de lectura.
- La URL de la API se obtiene de configuración de ambiente o runtime. Nunca fijes un host de producción en el código ni guardes secretos en `environments/`.

## Contrato API y cliente generado

- Genera el contrato mediante el backend; no lo mantengas manualmente. En Windows usa:

```powershell
.\scripts\mvnw-jdk25.ps1 "-Dtest=OpenApiContractIntegrationTest" test
```

- La entrada canónica del generador TypeScript es `target/openapi/inventory-api-v1.json`.
- La Fase 2 debe incorporar un comando reproducible para generar el cliente, con herramienta y versión fijadas. Separa claramente el código generado del adaptador escrito a mano.
- Nunca edites archivos generados. Si el cliente es incorrecto, corrige annotations/DTOs/controllers o la configuración del generador, vuelve a generar y añade la prueba correspondiente.
- No dupliques manualmente interfaces que ya proporciona el cliente generado. Los modelos exclusivos de presentación sí pertenecen a cada feature.
- Si cambia un endpoint, DTO, código de error, paginación o autorización: actualiza pruebas del backend, regenera OpenAPI y cliente, adapta la UI y actualiza la documentación en el mismo cambio.
- Trata `PageResponse` y `ApiError` como contratos compartidos. La lógica del cliente usa `code` y `validationErrors`, no analiza textos variables de `message` o `error`.

## Seguridad no negociable del navegador

La decisión SEC-02 rige todo el MVP:

- Access token y refresh token viven exclusivamente en memoria y tienen un único propietario: el servicio de sesión.
- Está prohibido persistirlos en `localStorage`, `sessionStorage`, IndexedDB, Cache API, cookies accesibles desde JavaScript, service workers, stores persistidos, parámetros de URL o mecanismos de sincronización entre pestañas.
- No incluyas tokens, contraseñas o credenciales en logs, telemetría, analytics, mensajes, estado serializable, capturas ni errores.
- El interceptor añade `Authorization: Bearer` únicamente a peticiones cuyo origen coincide exactamente con la API configurada. No envía credenciales a URLs arbitrarias.
- Login, refresh y logout no entran en ciclos de autorrenovación. Varias respuestas `401` concurrentes producen un solo refresh; las peticiones esperan ese resultado.
- Sustituye el par de tokens de forma atómica sólo tras un refresh correcto. Un `401` del refresh limpia inmediatamente la sesión y conduce al login sin bucles.
- Logout intenta revocar el refresh token y siempre limpia la memoria, incluso si la petición falla.
- Una recarga, una pestaña nueva o la restauración del navegador exige login. No intentes ocultar esta consecuencia con persistencia.
- No habilites `withCredentials`, cookies de autenticación ni persistencia sin una nueva decisión de arquitectura, cambios coordinados de backend/CORS y protección CSRF probada.
- Guards, menús y botones mejoran la experiencia, pero la API sigue siendo la autoridad. No interpretes ocultar controles como una medida de autorización.
- Evita `bypassSecurityTrust*`, HTML dinámico no sanitizado y dependencias que requieran relajar CSP/Trusted Types. Cualquier excepción exige justificación, revisión y pruebas de XSS.

## Roles y navegación

Aplica la matriz vigente del backend y verifícala contra Spring Security/OpenAPI antes de modificar permisos:

| Capacidad | `ADMIN` | `INVENTORY_MANAGER` | `SALES` |
|---|:---:|:---:|:---:|
| Consultar productos, almacenes e inventario | Sí | Sí | Sí |
| Gestionar productos, almacenes e inventario | Sí | Sí | No |
| Proveedores, compras, transferencias y conteos | Sí | Sí | No |
| Clientes y pedidos | Sí | No | Sí |
| Usuarios y roles | Sí | No | No |

- Define metadata de roles en rutas y reutiliza una sola política para guards, navegación y acciones; evita cadenas de roles dispersas por templates.
- Proporciona `/forbidden` para falta de permiso y una página para rutas desconocidas. Diferencia claramente sesión ausente (`401`) de permiso insuficiente (`403`).
- Después de cambios sensibles de contraseña, roles, estado o revocación, asume que las sesiones anteriores dejan de ser válidas y actualiza la UI en consecuencia.

## Estados, errores y operaciones de UI

Toda página o flujo de datos debe contemplar, cuando aplique:

- carga inicial y actualización;
- resultado vacío;
- error recuperable con acción de reintento segura;
- validación por campo y resumen accesible;
- conflicto de negocio `409`;
- sesión vencida `401` y permiso insuficiente `403`;
- rate limit `429`, respetando `Retry-After` y deshabilitando temporalmente la acción;
- confirmación de acciones sensibles;
- prevención de doble envío;
- éxito explícito y reconciliación/refresco de los datos.

Muestra el `X-Correlation-ID`/`correlationId` en errores de soporte sin revelar datos sensibles. No reintentes automáticamente mutaciones salvo que el contrato sea idempotente y el comportamiento esté probado. En recepciones, distingue visualmente `201` (creada) de `200` (reintento ya procesado).

Las tablas remotas implementan paginación y filtros del servidor; no descargues colecciones completas para simular reportes o dashboards. Conserva selecciones y filtros de forma predecible, sin persistir credenciales.

## Reglas de negocio que no se deben romper

- Nunca permitir inventario físico o disponible inválido/negativo.
- Diferenciar existencias físicas, reservadas, disponibles y en tránsito por almacén.
- Una operación afecta sólo el almacén seleccionado.
- Reservar reduce disponible, no existencia física; confirmar o cancelar afecta el inventario exactamente una vez.
- Operaciones multiartículo son atómicas: un fallo revierte todo.
- Recepciones con la misma referencia y contenido no duplican stock.
- Transferencias conservan origen + tránsito + destino y no usan stock reservado.
- Publicar dos veces un conteo no duplica ajustes.
- Bajas de productos o almacenes respetan saldos y documentos pendientes.
- Nunca se deshabilita al último administrador activo.

La UI debe representar los estados y transiciones que devuelve la API, no inventar transiciones locales ni suponer que una acción tuvo éxito antes de recibir y reconciliar la respuesta.

## Flujo obligatorio antes de implementar

1. Lee estas reglas, el plan y las decisiones/documentación del área.
2. Revisa el estado de Git y los cambios existentes.
3. Inspecciona controllers, DTOs, seguridad, OpenAPI y pruebas del dominio afectado; no deduzcas el contrato sólo del plan.
4. Identifica fase, roles, reglas de negocio, estados de UI y criterios de aceptación relevantes.
5. Define el cambio mínimo y las pruebas que demostrarán su funcionamiento.
6. Si necesitas una dependencia nueva, justifica su necesidad, comprueba compatibilidad/licencia/mantenimiento, fija la versión y evita duplicar capacidades de Angular o de las dependencias existentes.

## Pruebas y verificación

Añade o actualiza pruebas con el cambio, no después. Usa la comprobación proporcional al área:

### Si se modifica el backend

- Pruebas unitarias para reglas de services y casos límite.
- Pruebas de integración con Testcontainers para persistencia, transacciones, concurrencia, migraciones y seguridad HTTP.
- En Windows, con Docker activo:

```powershell
.\scripts\mvnw-jdk25.ps1 verify
.\scripts\mvnw-jdk25.ps1 spotbugs:check
```

No sustituyas PostgreSQL por una base en memoria en pruebas que validan SQL, locks o transacciones.

### Si se modifica el FrontEnd

- Ejecuta mediante los scripts versionados de `frontend/package.json`: formato/comprobación de formato, lint, pruebas unitarias o de componentes y build de producción.
- La fundación debe proporcionar scripts reproducibles para `lint`, `test`, `build`, `e2e` y `generate:api`; CI debe usar el lockfile.
- Prueba guards y permisos por rol, formularios, estados de carga/vacío/error, mapeo de `ApiError`, `429`, prevención de doble envío y accesibilidad básica.
- La sesión requiere pruebas específicas de tokens sólo en memoria, un único refresh concurrente, sustitución atómica, exclusión de endpoints de auth, fallo de refresh y limpieza de logout.
- Añade E2E para el flujo crítico modificado. Usa dobles de red en pruebas unitarias y una API real controlada para aceptación/integración; no hagas depender pruebas deterministas de servicios externos.

### Si cambia el contrato entre ambos

- Ejecuta la prueba de OpenAPI, regenera el cliente y verifica backend y FrontEnd.
- Revisa que no aparezcan rutas fuera de `/api/v1`, tipos duplicados ni ediciones manuales del generado.

### Excepciones

Los cambios exclusivamente documentales no requieren compilar. Si una verificación necesaria no puede ejecutarse por falta de Docker, JDK, Node u otra condición, no declares éxito total: indica exactamente qué ejecutaste, qué quedó pendiente y por qué.

## Definition of Done

Una funcionalidad sólo está terminada cuando:

- comportamiento, permisos y reglas de negocio coinciden con el contrato vigente;
- OpenAPI y cliente generado están sincronizados si corresponde;
- existe migración Flyway nueva si cambió el esquema;
- la UI cubre estados normales, vacíos, errores, conflicto, rate limit, permiso, doble envío y éxito aplicables;
- pasan las pruebas unitarias, de integración/componente y E2E pertinentes;
- se revisaron responsive, teclado y accesibilidad básica;
- no se persistieron ni registraron tokens, secretos o datos sensibles;
- se actualizaron README, decisiones, runbooks o plan cuando el cambio invalida su contenido;
- el reporte final enumera archivos modificados, pruebas/comandos ejecutados, resultados y cualquier riesgo o verificación pendiente.

## Prioridades permanentes

En caso de tensión entre objetivos, prioriza en este orden:

1. Seguridad de credenciales y autorización del servidor.
2. Integridad transaccional, idempotencia y trazabilidad del inventario.
3. Compatibilidad explícita con el contrato API.
4. Accesibilidad y claridad de estados para el usuario.
5. Simplicidad, mantenibilidad y rendimiento medido.

No sacrifiques una prioridad superior para reducir código o acelerar una entrega.
