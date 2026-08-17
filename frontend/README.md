# FrontEnd de Inventario

Fundación standalone del cliente Angular de Inventory API. Esta entrega corresponde a `F2-01`: incluye routing, estilos globales mínimos y pruebas unitarias, pero todavía no incorpora autenticación, acceso a la API ni módulos de negocio.

## Versiones fijadas

| Herramienta           |   Versión |
| --------------------- | --------: |
| Angular y Angular CLI |  `22.0.6` |
| Angular build tools   |  `22.1.4` |
| Node.js               | `24.18.0` |
| npm                   | `11.16.0` |
| TypeScript            |   `6.0.2` |
| RxJS                  |   `7.8.2` |

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
npm test -- --watch=false
```

Durante el desarrollo puedes mantenerlas observando cambios:

```powershell
npm test -- --watch
```

## Build de producción

```powershell
npm run build -- --configuration production
```

Los artefactos se escriben en `dist/inventory-frontend/` y no se versionan.
