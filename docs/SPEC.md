# SPEC — API de Gestión de Pólizas (Módulo 2)

> Fuente de verdad del módulo 2. Primero se escribe aquí qué debe hacer el sistema,
> luego se escriben las pruebas desde este documento, y por último el código.
> Cada regla (RN) y cada caso de aceptación (CA) tiene un ID que se cita en las pruebas.

## 1. Contexto y alcance

Plataforma de gestión de pólizas de arrendamiento de inmuebles.

- **Individual**: un solo riesgo. El tomador y asegurado es el arrendatario; el beneficiario, el arrendador.
- **Colectiva**: uno o muchos riesgos. Pensada para inmobiliarias y administraciones de copropiedades.

**Dentro del alcance:** los 6 endpoints del enunciado, el mock del CORE (`/core-mock/evento`),
seguridad mínima por header y validaciones de negocio.
**Fuera del alcance:** creación de pólizas por API (se cargan datos de ejemplo al arrancar),
envío real de correo/SMS (se simula en el log), autenticación real, persistencia permanente (H2 en memoria).

## 2. Stack

Java 21 · Spring Boot 3.5 · Spring Web · Spring Data JPA · H2 · Bean Validation · Lombok · JUnit 5 · Mockito · MockMvc.

## 3. Modelo de datos

**Poliza**: `id`, `tipo` (INDIVIDUAL | COLECTIVA), `estado` (ACTIVA | RENOVADA | CANCELADA),
`fechaInicio`, `vigenciaMeses`, `canonMensual` (BigDecimal 2 decimales), `prima` (BigDecimal 2 decimales), `riesgos` (1..N).

**Riesgo**: `id`, `poliza` (N..1), `arrendatario`, `direccionInmueble`, `estado` (ACTIVO | CANCELADO).

Invariante: `prima = canonMensual × vigenciaMeses`.

## 4. Reglas de negocio

| ID | Regla |
|---|---|
| RN-01 | Una póliza individual solo puede tener 1 riesgo; no admite agregar más. |
| RN-02 | Solo una póliza colectiva admite agregar riesgos. |
| RN-03 | No se puede renovar una póliza cancelada. |
| RN-04 | Renovar: `canon nuevo = canon × (1 + IPC)` redondeado a 2 decimales (HALF_UP); `prima = canon nuevo × vigenciaMeses`; estado pasa a RENOVADA. |
| RN-05 | Cancelar una póliza cancela todos sus riesgos. |
| RN-06 | No se puede cancelar una póliza ya cancelada, ni un riesgo ya cancelado. |
| RN-07 | No se pueden agregar riesgos a una póliza cancelada. |
| RN-08 | Toda operación que modifica el estado de una póliza o riesgo notifica al CORE con el evento `ACTUALIZACION` y el id de la póliza. |
| RN-09 | Si el CORE falla o no responde, la operación local NO se revierte; el fallo se registra en el log. Toda llamada al CORE tiene un tiempo máximo (`core.timeout`). |
| RN-10 | Todas las rutas exigen el header `x-api-key` con el valor configurado, excepto `/h2-console` y la documentación (`/swagger-ui`, `/v3/api-docs`), que no exponen datos de pólizas. |
| RN-11 | Una póliza renovada puede volver a renovarse. La renovación mueve `fechaInicio` un periodo de vigencia adelante. |
| RN-12 | Si dos operaciones modifican la misma póliza o riesgo al mismo tiempo, la que llega tarde se rechaza con 409 y no sobrescribe a la otra (bloqueo optimista). |
| RN-13 | Al renovar o cancelar una póliza se avisa al tomador por correo y SMS (simulados en el log con `[NOTIFICACION]`). Si la operación es rechazada, no se avisa. |

Configuración: `polizas.ipc` (por defecto `0.0500`), `polizas.api-key` (por defecto `123456`), `core.base-url`, `core.timeout` (por defecto `2s`).

## 5. Contrato de la API

Todas las respuestas de error usan el formato `{ timestamp, status, error, mensaje }`.

| Método y ruta | Descripción | Éxito | Errores |
|---|---|---|---|
| `GET /polizas?tipo=&estado=` | Lista pólizas; filtros opcionales y combinables | 200 + lista | 400 filtro inválido · 401 |
| `GET /polizas/{id}/riesgos` | Riesgos de la póliza | 200 + lista | 404 póliza · 401 |
| `POST /polizas/{id}/renovar` | Renueva (RN-03, RN-04) | 200 + póliza | 404 · 409 cancelada · 401 |
| `POST /polizas/{id}/cancelar` | Cancela póliza y sus riesgos (RN-05) | 200 + póliza | 404 · 409 ya cancelada · 401 |
| `POST /polizas/{id}/riesgos` | Agrega riesgo `{arrendatario, direccionInmueble}` (RN-01, RN-02, RN-07) | 201 + riesgo | 400 body inválido · 404 · 409 · 401 |
| `POST /riesgos/{id}/cancelar` | Cancela un riesgo | 200 + riesgo | 404 · 409 ya cancelado · 401 |
| `POST /core-mock/evento` | Mock del CORE: registra en log `{evento, polizaId}` | 202 | 400 body inválido · 401 |

## 6. Casos de aceptación (Given / When / Then)

| ID | Caso | Regla |
|---|---|---|
| CA-01 | Dado una póliza ACTIVA con canon 1.500.000 y 12 meses, cuando se renueva, entonces canon = 1.575.000,00, prima = 18.900.000,00 y estado RENOVADA | RN-04 |
| CA-02 | Dado un canon con decimales (1.000.000,33), cuando se renueva, entonces el canon se redondea HALF_UP a 2 decimales (1.050.000,35) | RN-04 |
| CA-03 | Dado una póliza CANCELADA, cuando se renueva, entonces 409 y no se notifica al CORE | RN-03, RN-08 |
| CA-04 | Dado una póliza RENOVADA, cuando se renueva de nuevo, entonces el canon vuelve a subir con el IPC | RN-11 |
| CA-05 | Dado una póliza colectiva con riesgos, cuando se cancela, entonces la póliza y todos sus riesgos quedan CANCELADOS | RN-05 |
| CA-06 | Dado una póliza ya cancelada, cuando se cancela, entonces 409 | RN-06 |
| CA-07 | Dado una póliza individual, cuando se agrega un riesgo, entonces 409 y sigue con un solo riesgo | RN-01, RN-02 |
| CA-08 | Dado una póliza colectiva activa, cuando se agrega un riesgo válido, entonces 201 y el riesgo aparece en el listado | RN-02 |
| CA-09 | Dado una póliza colectiva cancelada, cuando se agrega un riesgo, entonces 409 | RN-07 |
| CA-10 | Dado un body sin `arrendatario`, cuando se agrega un riesgo, entonces 400 con el campo en el mensaje | validación |
| CA-11 | Dado un riesgo activo, cuando se cancela, entonces queda CANCELADO y se notifica al CORE con el id de su póliza | RN-08 |
| CA-12 | Dado un riesgo cancelado, cuando se cancela, entonces 409 | RN-06 |
| CA-13 | Dado ids inexistentes, cuando se consulta o modifica, entonces 404 | contrato |
| CA-14 | Dado pólizas de distintos tipos y estados, cuando se filtra por tipo y/o estado, entonces solo vuelven las que coinciden | contrato |
| CA-15 | Dado un valor de filtro inválido (`tipo=XYZ`), entonces 400 | contrato |
| CA-16 | Dado cualquier petición sin `x-api-key` o con una incorrecta, entonces 401 | RN-10 |
| CA-17 | Dado que el CORE no responde (conexión rechazada o colgado), cuando se notifica, entonces no se lanza error y la espera no supera `core.timeout` | RN-09 |
| CA-18 | Dado un evento válido en `/core-mock/evento`, entonces 202; con `polizaId` ausente, 400 | contrato |
| CA-19 | Dadas dos copias de la misma póliza modificadas en paralelo, cuando se guarda la segunda, entonces se rechaza (409) | RN-12 |
| CA-20 | Dada una renovación o cancelación exitosa, entonces se avisa al tomador; si la operación es rechazada, no se avisa | RN-13 |
| CA-21 | La documentación OpenAPI (`/v3/api-docs`) y Swagger UI responden sin api key y declaran el esquema de seguridad `x-api-key` | RN-10 |

## 7. Decisiones de diseño

- **Capas**: controller (HTTP) → service (reglas) → repository (datos). El controller no contiene reglas.
- **Hexagonal en la integración**: el service depende del puerto `CorePort`, no del adapter HTTP; se puede cambiar por SOAP/cola sin tocar el negocio.
- **DTOs (records)** en lugar de exponer entidades: desacopla el contrato del modelo de BD y evita problemas de carga perezosa.
- **BigDecimal** para dinero: `double` produce errores de redondeo binario.
- **Excepciones de dominio** traducidas a HTTP en un único `@RestControllerAdvice`.
- **Fallo del CORE no bloqueante** (RN-09) y con timeout. Mejora futura: patrón Outbox + reintentos.
- **Bloqueo optimista** (`@Version`, RN-12) en lugar de bloqueos de BD: no frena lecturas y detecta escrituras simultáneas.
- **IPC configurable** por propiedad, no fijo en el código.
- **Notificaciones simples** (RN-13): `NotificacionService` concentra el envío; hoy escribe en el log y mañana puede llamar a un proveedor real sin tocar las reglas.
- **Contrato documentado con OpenAPI** (Swagger UI), con la api-key declarada como esquema de seguridad.

## 8. Trazabilidad

Los nombres de los tests incluyen el ID del caso (por ejemplo `CA01_...`). Para ver la cobertura de reglas,
buscar el ID en `src/test`.
