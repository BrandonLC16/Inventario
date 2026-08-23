# FrontEnd de Inventario

Cliente standalone Angular de Inventory API. `F2-01`–`F2-08` establecieron la fundación, la sesión no persistente, el cliente generado, el sistema visual y las comprobaciones reproducibles. `F3-01` incorporó el catálogo de productos, `F3-02` el catálogo de almacenes, `F3-03` los saldos de inventario de `MAIN` y por almacén y `F3-04` la configuración de mínimos y activación por almacén; los demás dominios de negocio continúan como entregas posteriores del plan.

## Versiones fijadas

| Herramienta           |   Versión |
| --------------------- | --------: |
| Angular y Angular CLI |  `22.0.6` |
| Angular Material/CDK  |  `22.0.6` |
| Angular build tools   |  `22.1.4` |
| OpenAPI Generator CLI |  `7.24.0` |
| Wrapper npm OpenAPI   |  `2.40.1` |
| Node.js               | `24.18.0` |
| npm                   | `11.16.0` |
| TypeScript            |   `6.0.2` |
| RxJS                  |   `7.8.2` |
| angular-eslint        |  `22.1.0` |
| ESLint                |  `10.8.1` |
| typescript-eslint     |  `8.67.0` |
| Playwright Test       |  `1.62.1` |

Angular `22.0.x` admite oficialmente Node.js `^22.22.3 || ^24.15.0 || ^26.0.0` y TypeScript `>=6.0.0 <6.1.0`; por tanto, las versiones elegidas pertenecen a la matriz compatible. Consulta la [tabla oficial de compatibilidad](https://angular.dev/reference/versions).

Las dependencias usan versiones exactas, `package-lock.json` es la fuente reproducible de la instalación y `.nvmrc` fija la versión elegida de Node.js. No uses `npm install` para una instalación normal ni actualices dependencias como parte de otra entrega.

## Requisitos

- Node.js `24.18.0`.
- npm `11.16.0`, declarado también mediante `packageManager`.

Comprueba el entorno desde `frontend/`:

```powershell
node --version
npm --version
```

## Instalación limpia

```powershell
cd frontend
npm ci
npm run e2e:install
```

El segundo comando descarga únicamente el Chromium asociado a la versión fijada de Playwright. En CI se usa `playwright install --with-deps chromium` para instalar también sus bibliotecas del sistema.

## Ejecución local

```powershell
npm start
```

La aplicación queda disponible en `http://localhost:4200/` y recarga al detectar cambios.

## Sesión, shell y navegación por roles

El shell común vive en `src/app/layout/` e incluye encabezado, menú lateral adaptable, breadcrumbs derivados de metadata de rutas, enlace para saltar al contenido y páginas específicas para acceso no permitido y rutas desconocidas. En anchos menores de `60rem`, la navegación se convierte en un panel temporal; por debajo de `42rem`, el encabezado reduce la información secundaria.

`/login` autentica con el cliente generado y obtiene después la identidad vigente mediante `/api/v1/auth/me`. El encabezado muestra esa identidad y permite cerrar la sesión. La matriz de roles controla el menú y los guards con esta política:

| Área                                               | `ADMIN` | `INVENTORY_MANAGER` | `SALES` |
| -------------------------------------------------- | :-----: | :-----------------: | :-----: |
| Resumen, productos, almacenes, inventario y perfil |   Sí    |         Sí          |   Sí    |
| Proveedores, compras, transferencias y conteos     |   Sí    |         Sí          |   No    |
| Clientes y pedidos                                 |   Sí    |         No          |   Sí    |
| Usuarios                                           |   Sí    |         No          |   No    |

`SessionService` es el único propietario del access token y el refresh token. Conserva el par en un único Signal privado, lo reemplaza de forma atómica sólo cuando la respuesta de login o refresh está completa y nunca lo escribe en Web Storage, IndexedDB, Cache API, cookies, service workers ni estado sincronizado. Una recarga, una pestaña nueva o la restauración del navegador requieren login.

El interceptor añade Bearer únicamente cuando el origen de la petición coincide exactamente con `apiBaseUrl`. Excluye login, refresh y logout del token y de la autorrenovación; varias respuestas `401` comparten un solo refresh. Un `401` de esa renovación limpia la memoria y vuelve a `/login`. Logout limpia primero la memoria e intenta revocar el refresh incluso si la API falla.

La matriz canónica está en `src/app/core/navigation/app-navigation.ts` y alimenta tanto las rutas como el menú y el guard por roles. Ocultar una opción o redirigir a `/forbidden` sólo mejora la experiencia: **la API siempre es la autoridad y debe volver a autorizar cada petición**.

Los catálogos de productos y almacenes son features lazy. Ambos permiten lectura a los tres roles y reservan alta, edición y baja/desactivación para `ADMIN` e `INVENTORY_MANAGER`. Almacenes usa exclusivamente `page` y `size` del servidor: no ofrece ni simula búsqueda local. Su desactivación requiere confirmación, conserva visible el registro inactivo y presenta de forma segura los conflictos por existencias, reservas o documentos abiertos.

`/inventory` muestra exclusivamente los saldos del almacén determinista `MAIN`; es un alias compatible de la API y nunca representa un total multi-almacén. `/warehouses/:id/inventory` consulta una ubicación concreta. Ambas vistas muestran existencias físicas, reservadas y disponibles con paginación remota. Cada página combina saldos con `sku`/`name` desde los settings del mismo almacén mediante `productId`: son dos peticiones por página, no una petición por fila, y cualquier diferencia de almacén, cardinalidad o IDs se rechaza como respuesta inconsistente.

`/warehouses/:id/settings` pagina remotamente los mínimos y la activación de cada producto en una ubicación. Los tres roles consultan y sólo `ADMIN` e `INVENTORY_MANAGER` modifican. `InventorySettingResponse.active` pertenece exclusivamente al almacén: el contrato no incluye ahí el estado global del producto, por lo que la pantalla lo consulta sólo al abrir una fila, lo presenta separado y de sólo lectura y enlaza al catálogo; así evita tanto fusionar conceptos como introducir N+1 en el listado. Después de cada `PUT` con `204`, el adaptador vuelve a ejecutar el `GET` individual y sólo entonces reconcilia la fila. Un `409` al desactivar conserva intacto el formulario y muestra orientación sobre stock/reservas junto con la referencia de soporte.

Con teclado, el enlace inicial salta al contenido, abrir el menú móvil mueve el foco a su primer enlace, `Escape` lo cierra y devuelve el foco al botón, y cada navegación enfoca el encabezado principal de la nueva vista.

## Manejo común de errores HTTP

`ApiErrorService` transforma `HttpErrorResponse` mediante el `code` estable del backend y usa el estado HTTP sólo como fallback. Nunca muestra ni toma decisiones a partir de `message`, `error` o `path`; tampoco conserva el cuerpo original en el modelo de presentación. Cubre autenticación `401`, autorización `403`, recurso API ausente `404`, conflicto `409`, rate limit `429`, validación y fallback seguro para respuestas incompletas o no JSON.

Los errores API usan `data-error-source="api"`, mientras que `/forbidden` y la ruta Angular desconocida usan `data-error-source="routing"`. Así, un `404` del servidor se presenta con referencia de soporte y no se confunde con una URL inexistente del cliente.

`validationErrors` se valida antes de asociarlo únicamente a controles presentes, incluidos paths de arreglos como `items[0].quantity`. `Retry-After` acepta segundos o fecha HTTP, tiene un límite defensivo de 24 horas y bloquea temporalmente la acción sin ejecutarla otra vez. El componente común no ofrece reintento salvo que el consumidor lo habilite explícitamente para una operación segura.

La referencia de soporte se toma primero de `X-Correlation-ID` y, como fallback, de `correlationId`; sólo acepta el formato seguro del backend. Se muestra en un campo de sólo lectura seleccionable y tiene una acción accesible para copiarla. El OpenAPI vigente todavía no publica el schema de `ApiError`, por lo que esta capa valida `HttpErrorResponse.error` como `unknown` y permanece fuera del cliente generado.

## Sistema visual compartido

La ruta interna `/design-system` muestra los fundamentos y los componentes de `F2-04` en una sola vista adaptable. No forma parte de un dominio ni introduce reglas de negocio.

Los tokens SCSS de `src/styles/` definen colores semánticos, tipografía del sistema sin descargar fuentes externas, una escala de espaciado basada en `4 px`, radios y elevaciones. El tema Material 3 usa las paletas Azure y Spring Green; los tokens propios conservan nombres semánticos para que una feature no dependa de un tono concreto.

Los pares principales cumplen contraste WCAG 2.2 AA: texto general `#17233b` sobre blanco, texto secundario `#526078` sobre blanco, primario `#244f9e` sobre blanco y estados con texto oscuro sobre fondos suaves. El foco visible combina un contorno azul `#005fcc` con un halo blanco para distinguirse tanto en superficies claras como oscuras. Los controles Material mantienen un objetivo táctil mínimo de `44 px` y la vista cambia de 12 columnas a una o dos columnas según el espacio disponible.

`src/app/shared/` ofrece componentes standalone y presentacionales para:

- carga anunciada con `role="status"` y `aria-busy`;
- contenido vacío con acción opcional;
- error recuperable con reintento y referencia de soporte opcional;
- error API accesible con validaciones, espera y referencia copiable;
- confirmación modal con trampa de foco, cierre con `Escape` y restauración al disparador;
- feedback semántico de éxito, información, atención y error, con cierre opcional.

Todos reciben textos y emiten eventos; ninguna decisión de inventario, autorización o reintento vive en `shared/`.

## Cliente OpenAPI y configuración runtime

El contrato canónico se genera desde el backend en `target/openapi/inventory-api-v1.json`. El cliente TypeScript Angular vive en `src/app/core/api/generated/` y se reemplaza por completo en cada generación; sus archivos no se editan manualmente. Adaptadores, providers y pruebas escritas a mano permanecen en `src/app/core/api/`, fuera del árbol generado.

Con Docker activo, regenera primero el contrato desde la raíz del repositorio:

```powershell
.\scripts\mvnw-jdk25.ps1 "-Dtest=OpenApiContractIntegrationTest" test
```

Después, desde `frontend/`, regenera el cliente con las versiones fijadas:

```powershell
npm run generate:api
```

La configuración usa OpenAPI Generator `7.24.0`, el generador estable `typescript-angular` y versiones explícitas de Angular, RxJS y TypeScript. `npm run generate:api:check` toma hashes del árbol generado, vuelve a generarlo y falla si aparece cualquier archivo nuevo, eliminado o modificado.

La aplicación carga `public/config/runtime-config.json` antes de crear servicios API. `apiBaseUrl` debe ser exclusivamente un origen HTTP(S), sin credenciales, ruta, query ni fragmento. El archivo versionado apunta al backend local:

```json
{
  "apiBaseUrl": "http://localhost:8080"
}
```

En un despliegue, reemplaza este archivo estático por la configuración del entorno; no recompiles el cliente ni introduzcas secretos. El provider crea la configuración generada con `withCredentials: false`; la sesión sólo compara peticiones contra el origen ya validado.

CI regenera el OpenAPI con PostgreSQL/Testcontainers después del checkout y ejecuta `npm run generate:api:check` antes de lint, pruebas y builds. Así detecta tanto cambios del contrato como ediciones manuales o archivos generados obsoletos.

## Pruebas

Ejecuta una sola vez las pruebas unitarias y de componentes:

```powershell
npm test
```

Durante el desarrollo puedes mantenerlas observando cambios:

```powershell
npm run test:watch
```

Las 66 pruebas unitarias/de componente cubren la política de roles, guards, sesión en memoria, refresh single-flight y fallido, logout degradado, interceptor por origen, errores comunes y accesibilidad de componentes. Los E2E arrancan por sí solos el servidor Angular en `127.0.0.1:4200` y ejecutan Chromium sin reintentos:

```powershell
npm run e2e
```

Los 14 escenarios E2E cubren visitante, login y `/me`, menú y rutas de los tres roles, ausencia de persistencia y recarga, refresh rechazado, logout degradado, `403`, `409`, `429` con reloj controlado, correlation ID copiable, navegación por teclado y viewport móvil de `390×844`. Las respuestas difíciles se interceptan con datos inequívocamente sintéticos y nunca contienen secretos reales.

La integración controlada con la Inventory API real se conserva en las pruebas de backend basadas en PostgreSQL/Testcontainers, incluida `SecuritySessionIntegrationTest`, y el mismo workflow de CI las ejecuta junto con la comprobación del cliente OpenAPI. Los E2E no necesitan una base compartida ni credenciales versionadas, por lo que son deterministas y reproducibles localmente.

## Calidad

Formatea los archivos versionados del FrontEnd:

```powershell
npm run format
```

Comprueba formato y lint sin modificar archivos:

```powershell
npm run format:check
npm run lint
```

Ejecuta en secuencia formato, lint, pruebas unitarias/de componente, E2E y ambos builds:

```powershell
npm run check
```

El lint usa flat config de ESLint para TypeScript, componentes Angular, templates externos e inline, e incluye las reglas de accesibilidad recomendadas por angular-eslint.

## Builds y presupuestos

Build de desarrollo, sin optimización y con source maps:

```powershell
npm run build:development
```

Build optimizado de producción:

```powershell
npm run build
```

Los artefactos se escriben en `dist/inventory-frontend/` y no se versionan.

Los límites se fijaron en `F2-02` y se siguen comparando con la medición más reciente para dejar margen al crecimiento deliberado sin permitir que una incorporación accidental pase inadvertida:

| Configuración | Bundle inicial medido | Aviso inicial | Error inicial | Aviso por estilo | Error por estilo |
| ------------- | --------------------: | ------------: | ------------: | ---------------: | ---------------: |
| Desarrollo    |             `1.49 MB` |     `1.75 MB` |        `2 MB` |           `6 kB` |          `10 kB` |
| Producción    |           `326.95 kB` |      `350 kB` |      `450 kB` |           `6 kB` |          `10 kB` |

El umbral de desarrollo es mayor porque conserva código sin optimizar y source maps. El de producción permite aproximadamente `23 kB` antes del aviso y `123 kB` antes del error sobre la medición actual. Los estilos por componente se limitan de forma independiente para impedir que una sola vista concentre CSS excesivo. Todo error de budget hace fallar el build y CI ejecuta ambas configuraciones.

## Strictness y excepciones

TypeScript usa `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess` y comprobación consistente de mayúsculas/minúsculas. Angular activa `strictTemplates` además de las comprobaciones estrictas de inyección e inputs.

La única excepción deliberada es `skipLibCheck: true`: evita volver a comprobar declaraciones de tipos de dependencias externas, pero no relaja el código ni los templates de la aplicación. No se deshabilitó ninguna regla estricta para resolver un caso local.
