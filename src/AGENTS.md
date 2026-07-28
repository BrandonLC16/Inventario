# Proyecto Inventory API

## Arquitectura
- Java y Spring Boot.
- Arquitectura de monolito modular.
- Organizar el código por funcionalidad.
- Controllers solo gestionan HTTP.
- Las reglas de negocio pertenecen a los services.
- No exponer entidades JPA directamente; utilizar DTOs.

## Base de datos
- PostgreSQL.
- Todos los cambios de esquema deben usar Flyway.
- No permitir inventario negativo.
- Confirmar pedidos y descontar inventario en una sola transacción.

## Seguridad
- Spring Security y JWT.
- Nunca escribir secretos, contraseñas o tokens en los logs.
- Aplicar autorización por roles.

## Verificación
- Ejecutar las pruebas después de cada cambio.
- Añadir pruebas para cada regla de negocio nueva.
- Usar Testcontainers para las pruebas de integración.
- Mostrar un resumen de archivos modificados y pruebas ejecutadas.