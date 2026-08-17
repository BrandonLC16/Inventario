# SEC-02 — Refresh token en el navegador

- Estado: aceptada
- Fecha: 2026-08-17
- Alcance: cliente Angular MVP

## Decisión

El MVP usa una sesión no persistente. El access token y el refresh token se mantienen exclusivamente en memoria del proceso JavaScript. Al recargar, cerrar o restaurar la pestaña no se recupera la sesión y el usuario debe autenticarse de nuevo.

Queda prohibido guardar cualquiera de los tokens en:

- `localStorage`;
- `sessionStorage`;
- IndexedDB o Cache API;
- cookies accesibles desde JavaScript;
- estado persistido por librerías, service workers o mecanismos de sincronización entre pestañas.

Esta decisión conserva el contrato stateless actual: login y refresh devuelven ambos tokens en JSON, CORS no admite credenciales y CSRF permanece deshabilitado porque la API no usa credenciales ambientales del navegador.

## Reglas para el cliente Angular

El servicio de sesión en memoria es el único propietario de ambos tokens. El interceptor:

1. añade el access token sólo a peticiones dirigidas al origen configurado de esta API;
2. nunca lee ni escribe Web Storage, cookies, IndexedDB o estado persistido;
3. delega la renovación a un único coordinador para serializar peticiones concurrentes;
4. sustituye ambos tokens de forma atómica sólo después de una renovación correcta;
5. evita ciclos de reintento y no intenta renovar las rutas de login, refresh o logout;
6. elimina inmediatamente la sesión en memoria si el refresh falla con `401`.

El logout intenta revocar el refresh token y limpia siempre el estado en memoria, incluso si la petición de revocación falla. Los tokens no deben aparecer en logs, telemetría, mensajes de error ni herramientas de persistencia de estado.

## Consecuencias aceptadas

- Una recarga o una pestaña nueva requiere login.
- Cada pestaña mantiene una sesión independiente.
- Un XSS activo aún podría leer los tokens mientras la página está abierta; no persistirlos reduce su disponibilidad posterior, pero no sustituye las defensas contra XSS.
- El refresh token de 14 días no implica una sesión de navegador de 14 días en este MVP.

## Condición para habilitar persistencia

La sesión web persistente requiere una nueva decisión e implementación previa al cambio del cliente. La solución deberá usar un BFF que custodie el refresh token o una cookie de refresh `HttpOnly`, `Secure` y `SameSite`, y deberá incluir:

- protección CSRF probada para todas las operaciones que usan la cookie;
- orígenes CORS explícitos y credenciales habilitadas sólo donde sean necesarias;
- rotación, revocación y expiración de la cookie;
- pruebas que demuestren que el refresh token no aparece en JSON ni es accesible desde JavaScript.

No está permitido habilitar persistencia únicamente cambiando el interceptor, activando `withCredentials` o trasladando el token a `sessionStorage`.
