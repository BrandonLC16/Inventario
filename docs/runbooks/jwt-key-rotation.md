# Runbook — Rotación de claves de firma JWT

- Propietario: Seguridad y Operaciones
- Revisión mínima: cada 90 días y después de todo incidente relacionado
- Última actualización: 2026-08-17
- Estado: obligatorio ensayarlo en staging antes del primer despliegue productivo

## Alcance y limitación actual

La aplicación usa RS256 con un único par configurado mediante `JWT_PRIVATE_KEY_LOCATION` y `JWT_PUBLIC_KEY_LOCATION`. La clave se carga al iniciar cada réplica. Los JWT emitidos no incluyen `kid` y el decoder no consume JWKS ni acepta simultáneamente dos claves.

Por lo tanto, la rotación soportada actualmente es un corte coordinado de todas las réplicas:

- no se permite una actualización gradual con réplicas usando pares distintos;
- todos los access tokens firmados con la clave anterior quedan inválidos al activar la nueva;
- los refresh tokens no dependen de la clave RSA y siguen siendo utilizables para obtener un nuevo access token después del corte;
- la duración predeterminada del corte es operativa, pero la pérdida de la sesión access activa es una consecuencia de seguridad aceptada.

Hasta implementar la convivencia descrita al final de este documento, toda rotación requiere ventana de mantenimiento o drenado completo del tráfico de autenticación y API.

## Política y custodia

1. Rotar como máximo cada 90 días; una política corporativa más estricta prevalece.
2. Generar una clave RSA de al menos 3072 bits mediante una estación segura y cargarla inmediatamente en el gestor de secretos versionado. No generarla en CI, en el repositorio, en una imagen ni en un directorio compartido. La implementación actual necesita leer un PKCS#8 al arrancar; usar una clave no exportable de KMS/HSM requerirá primero integrar un proveedor de firma y no puede habilitarse sólo mediante configuración.
3. Mantener la clave privada cifrada en el gestor. La aplicación sólo recibe permiso de lectura sobre la versión activa y la obtiene mediante un montaje o archivo efímero de sólo lectura.
4. Separar permisos:
   - la identidad de runtime puede leer la clave activa, pero no crear, listar versiones históricas, exportar, rotar ni eliminar;
   - Operaciones puede desplegar una referencia de versión, pero no leer el valor privado;
   - Seguridad puede crear, habilitar, deshabilitar y destruir versiones con aprobación de dos personas;
   - CI, desarrolladores y cuentas humanas ordinarias no tienen acceso a claves productivas.
5. Si el proveedor obliga a montar archivos, usar un volumen de sólo lectura fuera de la imagen y del checkout. En Unix, directorio `0700`, clave privada `0400` y clave pública `0444`; en Windows, conceder lectura únicamente a la identidad del servicio y administradores autorizados.
6. Respaldar mediante el versionado cifrado y la replicación del gestor de secretos, no copiando archivos manualmente. Probar la restauración en un ambiente aislado al menos una vez por trimestre.
7. Registrar sólo metadatos no secretos: identificador de versión, huella SHA-256 de la clave pública, fecha de creación/activación/retiro, responsable y ticket. Nunca registrar PEM, material privado ni tokens de prueba.

## Preparación

Antes de cada rotación planificada:

1. Abrir un cambio aprobado con responsables, ventana, plan de reversión y huellas esperadas.
2. Confirmar que login, refresh y validación JWT están saludables y que no existe otro incidente activo.
3. Confirmar acceso al último respaldo recuperable del par vigente y auditar quién lo leyó desde la rotación anterior.
4. Crear un par nuevo como una versión independiente. No sobrescribir la versión vigente.
5. Validar fuera de producción que ambas mitades coinciden:

   ```bash
   openssl pkey -in inventory-private.pem -check -noout
   openssl pkey -in inventory-private.pem -pubout -outform DER | openssl dgst -sha256
   openssl pkey -pubin -in inventory-public.pem -outform DER | openssl dgst -sha256
   ```

   Las dos huellas deben ser idénticas. Los archivos de este ejemplo son temporales, deben crearse fuera del repositorio y eliminarse de forma segura al terminar.
6. Ejecutar el procedimiento completo en staging con la misma topología que producción y adjuntar al cambio la evidencia indicada en “Validación”. Si el ensayo falla, producción queda bloqueada.

## Rotación planificada con la implementación actual

1. Notificar la ventana y detener nuevas tareas administrativas que modifiquen sesiones.
2. Retirar todas las réplicas del balanceador y esperar a que terminen las peticiones en curso. No dejar tráfico servido por claves distintas.
3. Actualizar de forma atómica las referencias `JWT_PRIVATE_KEY_LOCATION` y `JWT_PUBLIC_KEY_LOCATION` al mismo identificador de versión nuevo.
4. Reiniciar o redesplegar todas las réplicas. Una recarga dinámica no está soportada.
5. Antes de reabrir tráfico, ejecutar en una réplica aislada las comprobaciones de “Validación”.
6. Incorporar todas las réplicas al balanceador y repetir el smoke test a través del endpoint público.
7. Mantener la versión anterior deshabilitada para emisión y accesible sólo al equipo de Seguridad durante la ventana de reversión aprobada. Después de esa ventana y de al menos un TTL máximo de access token, destruirla conforme a la política de retención.
8. Cerrar el cambio con las huellas, horarios, resultados y eventos de monitoreo. No adjuntar claves ni tokens.

## Validación obligatoria

Registrar resultado, hora y réplica para cada comprobación:

1. Todas las réplicas arrancan con el mismo identificador y huella pública esperados.
2. Un login emite un JWT RS256 que `/api/v1/auth/me` acepta.
3. Un access token emitido antes del corte recibe `401` después del cambio.
4. Un refresh token válido emitido antes del corte obtiene un par nuevo y el nuevo access token es aceptado.
5. Un JWT firmado con una clave desconocida recibe `401`.
6. Logout y revocación continúan funcionando.
7. No aparecen claves, tokens ni cuerpos sensibles en logs, trazas o telemetría.
8. Las tasas de login, refresh, `401`, `5xx` y latencia permanecen dentro de los umbrales operativos durante al menos 30 minutos.

## Reversión

Si alguna validación falla, mantener el tráfico drenado, restaurar juntas las referencias pública y privada de la versión anterior, reiniciar todas las réplicas y repetir las comprobaciones. Nunca combinar la clave privada de una versión con la pública de otra ni operar un conjunto mixto de réplicas.

Una clave marcada como comprometida no puede usarse para reversión.

## Respuesta ante compromiso

1. Declarar un incidente, preservar los registros de auditoría del gestor de secretos y retirar inmediatamente todas las réplicas del balanceador.
2. Deshabilitar la versión comprometida y bloquear nuevas lecturas. No borrar evidencia hasta que Seguridad lo autorice.
3. Crear un par nuevo desde una identidad y estación confiables y ejecutar el corte coordinado sin ventana de reversión hacia la clave comprometida.
4. Verificar que los access tokens firmados por la clave comprometida ya reciben `401`.
5. Determinar si también pudieron exponerse refresh tokens, credenciales o la base de datos. Si el alcance es incierto, revocar todas las familias refresh y elevar `access_token_version` mediante una operación transaccional aprobada; la aplicación no ofrece actualmente una revocación global de un solo paso.
6. Rotar las credenciales que permitieron acceder al gestor, revisar todas las lecturas/exportaciones y buscar JWT anómalos por sujeto, roles, `jti`, emisor, audiencia y tiempos.
7. Notificar conforme al plan de incidentes, documentar alcance y causa raíz, y destruir la versión comprometida cuando concluya la preservación de evidencia.

## Monitoreo y alertas

- Alertar por lecturas o exportaciones de la clave privada fuera de la identidad, ambiente u horario esperados.
- Alertar a los 75 días de edad de la clave y escalar diariamente hasta su rotación antes de 90 días.
- Monitorear fallos de carga o firma, aumentos de `401`/`5xx`, errores de refresh y diferencias de huella entre réplicas.
- Revisar mensualmente permisos, versiones habilitadas, respaldos y accesos del gestor de secretos.
- Una huella distinta entre réplicas es un incidente: drenar el conjunto afectado y corregirlo antes de devolverlo al balanceador.

## Evolución requerida para rotación sin interrupciones

La rotación gradual no estará soportada hasta implementar y probar todo lo siguiente:

1. Asignar a cada clave un `kid` único, opaco y no reutilizable, e incluirlo en cada JWT.
2. Verificar mediante un conjunto explícito de claves públicas o JWKS; la clave privada nunca se publica en JWKS.
3. Rechazar algoritmos inesperados y `kid` ausentes o desconocidos sin realizar consultas a destinos controlados por el token.
4. Desplegar primero la nueva clave pública en todos los verificadores, después empezar a firmar con la nueva privada, esperar al menos el TTL máximo de access token más la tolerancia de reloj y sólo entonces retirar la pública anterior.
5. Definir caché, refresco, disponibilidad y autenticidad de JWKS, evitando que un fallo de red elimine claves válidas prematuramente.
6. Añadir pruebas de convivencia, despliegue gradual, clave desconocida, rollback, expiración y retiro.

Hasta completar esa evolución, no debe añadirse un `kid` meramente informativo ni intentarse una rotación rolling.
