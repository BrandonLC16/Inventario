# FrontEnd de Inventario

Fundación standalone del cliente Angular de Inventory API. `F2-01` creó el proyecto, `F2-02` añadió las comprobaciones de calidad, `F2-03` incorporó el shell y la navegación por rol, `F2-04` definió el sistema visual y `F2-05` integró el cliente generado y la configuración runtime de la API. Todavía no incluye autenticación real ni módulos de negocio.

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
```

## Ejecución local

```powershell
npm start
```

La aplicación queda disponible en `http://localhost:4200/` y recarga al detectar cambios.

## Shell y navegación de demostración

El shell común vive en `src/app/layout/` e incluye encabezado, menú lateral adaptable, breadcrumbs derivados de metadata de rutas, enlace para saltar al contenido y páginas específicas para acceso no permitido y rutas desconocidas. En anchos menores de `60rem`, la navegación se convierte en un panel temporal; por debajo de `42rem`, el encabezado reduce la información secundaria.

El selector **Vista de rol** permite comprobar en memoria las tres identidades de prueba. Cambiar el rol vuelve al resumen y actualiza el menú con esta política:

| Área                                               | `ADMIN` | `INVENTORY_MANAGER` | `SALES` |
| -------------------------------------------------- | :-----: | :-----------------: | :-----: |
| Resumen, productos, almacenes, inventario y perfil |   Sí    |         Sí          |   Sí    |
| Proveedores, compras, transferencias y conteos     |   Sí    |         Sí          |   No    |
| Clientes y pedidos                                 |   Sí    |         No          |   Sí    |
| Usuarios                                           |   Sí    |         No          |   No    |

La identidad de demostración usa únicamente un Signal del servicio de sesión. No escribe en `localStorage`, `sessionStorage`, cookies ni otro almacenamiento, y una recarga restaura el rol inicial. No contiene tokens ni representa una autenticación real.

La matriz canónica está en `src/app/core/navigation/app-navigation.ts` y alimenta tanto las rutas como el menú y el guard de demostración. Ocultar una opción o redirigir a `/forbidden` sólo mejora la experiencia: **la API siempre es la autoridad y debe volver a autorizar cada petición**.

Con teclado, el enlace inicial salta al contenido, abrir el menú móvil mueve el foco a su primer enlace, `Escape` lo cierra y devuelve el foco al botón, y cada navegación enfoca el encabezado principal de la nueva vista.

## Sistema visual compartido

La ruta interna `/design-system` muestra los fundamentos y los componentes de `F2-04` en una sola vista adaptable. No forma parte de un dominio ni introduce reglas de negocio.

Los tokens SCSS de `src/styles/` definen colores semánticos, tipografía del sistema sin descargar fuentes externas, una escala de espaciado basada en `4 px`, radios y elevaciones. El tema Material 3 usa las paletas Azure y Spring Green; los tokens propios conservan nombres semánticos para que una feature no dependa de un tono concreto.

Los pares principales cumplen contraste WCAG 2.2 AA: texto general `#17233b` sobre blanco, texto secundario `#526078` sobre blanco, primario `#244f9e` sobre blanco y estados con texto oscuro sobre fondos suaves. El foco visible combina un contorno azul `#005fcc` con un halo blanco para distinguirse tanto en superficies claras como oscuras. Los controles Material mantienen un objetivo táctil mínimo de `44 px` y la vista cambia de 12 columnas a una o dos columnas según el espacio disponible.

`src/app/shared/` ofrece componentes standalone y presentacionales para:

- carga anunciada con `role="status"` y `aria-busy`;
- contenido vacío con acción opcional;
- error recuperable con reintento y referencia de soporte opcional;
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

En un despliegue, reemplaza este archivo estático por la configuración del entorno; no recompiles el cliente ni introduzcas secretos. El provider crea la configuración generada con `withCredentials: false`. Los tokens de sesión se incorporarán únicamente en memoria durante `F2-06`.

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

Ejecuta en secuencia todas las comprobaciones de `F2-02`:

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
| Desarrollo    |             `1.46 MB` |     `1.75 MB` |        `2 MB` |           `6 kB` |          `10 kB` |
| Producción    |           `280.09 kB` |      `350 kB` |      `450 kB` |           `6 kB` |          `10 kB` |

El umbral de desarrollo es mayor porque conserva código sin optimizar y source maps. El de producción permite aproximadamente `70 kB` antes del aviso y `170 kB` antes del error sobre la medición actual. Los estilos por componente se limitan de forma independiente para impedir que una sola vista concentre CSS excesivo. Todo error de budget hace fallar el build y CI ejecuta ambas configuraciones.

## Strictness y excepciones

TypeScript usa `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess` y comprobación consistente de mayúsculas/minúsculas. Angular activa `strictTemplates` además de las comprobaciones estrictas de inyección e inputs.

La única excepción deliberada es `skipLibCheck: true`: evita volver a comprobar declaraciones de tipos de dependencias externas, pero no relaja el código ni los templates de la aplicación. No se deshabilitó ninguna regla estricta para resolver un caso local.

El script `e2e` se añadirá en `F2-08` cuando exista su herramienta y sus escenarios; no se mantiene un placeholder que aparente una verificación inexistente.
