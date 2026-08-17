# FrontEnd de Inventario

Fundación standalone del cliente Angular de Inventory API. `F2-01` creó el proyecto y `F2-02` añadió comprobaciones estrictas, lint, formato y límites de bundle reproducibles. Todavía no incorpora autenticación, acceso a la API ni módulos de negocio.

## Versiones fijadas

| Herramienta           |   Versión |
| --------------------- | --------: |
| Angular y Angular CLI |  `22.0.6` |
| Angular build tools   |  `22.1.4` |
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

Los límites parten de los bundles medidos en `F2-01`/`F2-02` y dejan margen para el shell y Angular Material sin permitir que una incorporación accidental pase inadvertida:

| Configuración | Bundle inicial medido | Aviso inicial | Error inicial | Aviso por estilo | Error por estilo |
| ------------- | --------------------: | ------------: | ------------: | ---------------: | ---------------: |
| Desarrollo    |             `1.31 MB` |     `1.75 MB` |        `2 MB` |           `6 kB` |          `10 kB` |
| Producción    |           `189.46 kB` |      `350 kB` |      `450 kB` |           `6 kB` |          `10 kB` |

El umbral de desarrollo es mayor porque conserva código sin optimizar y source maps. El de producción permite aproximadamente `160 kB` antes del aviso y `260 kB` antes del error sobre la base actual. Los estilos por componente se limitan de forma independiente para impedir que una sola vista concentre CSS excesivo. Todo error de budget hace fallar el build y CI ejecuta ambas configuraciones.

## Strictness y excepciones

TypeScript usa `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess` y comprobación consistente de mayúsculas/minúsculas. Angular activa `strictTemplates` además de las comprobaciones estrictas de inyección e inputs.

La única excepción deliberada es `skipLibCheck: true`: evita volver a comprobar declaraciones de tipos de dependencias externas, pero no relaja el código ni los templates de la aplicación. No se deshabilitó ninguna regla estricta para resolver un caso local.

Los scripts `e2e` y `generate:api` se añadirán en `F2-08` y `F2-05`, respectivamente, cuando existan sus herramientas y artefactos; esta entrega no incorpora placeholders que aparenten verificaciones inexistentes.
