# FinCore

Aplicación de finanzas personales con un backend en **Java 21 y Spring Boot** y un frontend en **React**. Permite llevar cuentas, movimientos y transferencias, organizar el gasto por categorías, fijar presupuestos y objetivos de ahorro, controlar suscripciones y facturas, importar extractos en CSV y consultar analíticas.

- **La API** cubre todo lo anterior y está documentada con Swagger.
- **La web** cubre el uso diario: registro e inicio de sesión, resumen del periodo, cuentas, movimientos (con filtros, transferencias y anulación), presupuestos y categorías. El resto de funciones (objetivos, suscripciones, facturas, importación CSV, analítica detallada) se usan de momento desde la API.

## Funcionalidades

| Área | Qué se puede hacer |
|---|---|
| **Usuarios y seguridad** | Registro, login con JWT, refresh token con rotación, cierre de sesión, ver y cerrar sesiones abiertas, cambio y recuperación de contraseña, verificación de email, bloqueo tras varios intentos fallidos y límite de peticiones en los endpoints de autenticación |
| **Cuentas** | Crear, editar y consultar cuentas (banco, tarjeta, efectivo o ahorro), con su moneda. Cambiar su estado (activa, archivada o cerrada) siguiendo unas transiciones permitidas |
| **Movimientos** | Ingresos y gastos que actualizan el saldo, búsqueda con filtros y ordenación, anulación y reembolsos |
| **Transferencias** | Entre cuentas del mismo usuario, en una sola transacción |
| **Idempotencia** | La cabecera `Idempotency-Key` evita que un movimiento o una transferencia se registre dos veces si el cliente repite la petición |
| **Categorías** | Categorías del sistema y propias, con subcategorías, y **reglas** que categorizan los movimientos automáticamente |
| **Presupuestos** | Por categoría y periodo, con avisos al llegar al 80 % y al 100 % |
| **Objetivos de ahorro** | Aportaciones, progreso y aportación mensual recomendada |
| **Suscripciones y facturas** | Próximos cobros, gasto mensual y anual, y facturas con estados (pendiente, pagada, vencida, cancelada). Pagar una factura registra el gasto |
| **Importación CSV** | Importar movimientos desde un extracto, con detección de duplicados y resultado por fila |
| **Monedas** | Moneda base por usuario y tipos de cambio históricos (los mantiene un administrador) |
| **Analítica** | Resumen, evolución mensual, gasto por categoría, mayores gastos y comercios, comparativas, previsión de gastos recurrentes y un dashboard por mes, trimestre, año o rango |
| **Notificaciones** | Avisos dentro de la aplicación (presupuestos, objetivos, importaciones, facturas) |
| **Tareas programadas** | Marcar facturas vencidas, enviar recordatorios y limpiar datos caducados |
| **Auditoría y observabilidad** | Registro de acciones sensibles, métricas con Prometheus y un `X-Request-Id` en cada respuesta y en los logs |

## Tecnologías

- **Java 21**, **Spring Boot 3** (Web, Data JPA, Security, Validation, Actuator)
- **PostgreSQL 16** con **Flyway** (23 migraciones)
- **JWT** con jjwt y contraseñas con BCrypt
- **springdoc-openapi** (Swagger UI)
- **Micrometer + Prometheus**
- **JUnit 5**, **Spring Boot Test** y **Testcontainers**
- **Docker** y **Docker Compose**, **GitHub Actions**

**Frontend** (carpeta `frontend/`):

- **React 19** con **TypeScript** (modo estricto) y **Vite**
- **Tailwind CSS** con una paleta propia definida en `src/index.css`
- **TanStack Query** para los datos del servidor y **React Router** para la navegación
- **React Hook Form + Zod** para los formularios
- **Vitest** para los tests
- Se sirve con **nginx**, que también reenvía `/api` al backend

## Arquitectura

Es un **monolito modular**: un solo proyecto organizado por áreas de negocio (`accounts`, `transactions`, `budgets`, `identity`...). Cada módulo se divide en tres partes:

```
com.fincore.<modulo>
├── domain/          entidades y reglas que no dependen de Spring (por ejemplo, las transiciones de estado)
├── application/     casos de uso: servicios, eventos y excepciones de negocio
└── infrastructure/  lo que habla con el exterior: controladores REST, DTOs y repositorios
```

Los módulos se comunican con **eventos de Spring** cuando un efecto secundario no debe acoplarlos. Por ejemplo, al superar un presupuesto se publica un evento y el módulo de notificaciones lo recoge, sin que presupuestos sepa que las notificaciones existen.

## Decisiones técnicas que conviene explicar

- **Dinero con `BigDecimal`, nunca con `double`.** La clase `Money` guarda importe y moneda, redondea con `HALF_EVEN` (redondeo bancario) y no permite operar con monedas distintas.
- **Idempotencia.** Un móvil con mala cobertura puede reenviar la misma petición. Con `Idempotency-Key`, la segunda vez se devuelve la respuesta de la primera en lugar de duplicar el movimiento. Si se reutiliza la clave con datos distintos, se rechaza.
- **Refresh tokens con rotación.** Cada refresh token sirve una vez. Se guardan como hash, y reutilizar uno ya usado se detecta.
- **Fuerza bruta.** Tras varios intentos fallidos la cuenta se bloquea un tiempo, y los endpoints de autenticación tienen un límite de peticiones por minuto.
- **El primer administrador** se nombra por configuración (`ADMIN_EMAIL`): no existe ningún endpoint para hacerse administrador.
- **Analítica con SQL.** Las agregaciones se hacen en la base de datos, no cargando todos los movimientos en memoria.
- **Esquema con Flyway e Hibernate en `validate`.** El esquema solo cambia con migraciones versionadas.
- **Errores con un formato único** (`GlobalExceptionHandler`), sin trazas ni detalles internos en la respuesta.

### En el frontend

- **Mismo origen.** En desarrollo Vite y en Docker nginx reenvían `/api` al backend, así que el navegador solo habla con un origen y no hace falta abrir CORS.
- **Renovación del token sin que se note.** Si una petición recibe un 401, se renueva el access token con el refresh token y se repite. Como el backend rota el refresh token (cada uno sirve una vez), varias peticiones que fallan a la vez comparten la misma renovación en lugar de gastar el token dos veces.
- **Idempotencia también desde la web.** Cada formulario de movimiento o transferencia genera su `Idempotency-Key` al abrirse; si el usuario reintenta tras un fallo de red, el backend no duplica el movimiento.
- **Filtros en la URL.** Los filtros de movimientos se guardan en la dirección (`?accountId=...&page=2`): al recargar o compartir el enlace se ve la misma lista.
- **Mensajes de error para personas.** Los mensajes de la API están pensados para quien la integra (llevan ids o nombres de estados). El frontend traduce los códigos de error que puede provocar (`ACCOUNT_BALANCE_NOT_ZERO`, `CATEGORY_IN_USE`...) a un texto claro y, si no conoce el código, muestra el mensaje de la API.
- **Sin librerías de componentes.** Botones, campos, diálogos (con el `<dialog>` nativo) e iconos (SVG propios) están hechos a mano: son pocos y así el bundle queda pequeño.

## Requisitos

- **Docker** con Docker Compose, para la opción rápida.
- Para ejecutar sin Docker: **Java 21** y un **PostgreSQL 16**. El proyecto incluye el wrapper de Maven (`mvnw`), así que no hace falta instalar Maven. Para el frontend, **Node.js 22** o superior.

## Puesta en marcha con Docker

```bash
cp .env.example .env
```

Abre `.env` y rellena `JWT_SECRET` con un valor aleatorio de al menos 32 caracteres, por ejemplo con `openssl rand -base64 48`. Sin él la aplicación no arranca, a propósito.

```bash
docker compose up -d --build
```

| Qué | URL |
|---|---|
| **Web** | http://localhost:3000 |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Estado | http://localhost:8080/actuator/health |

En la web, crea un usuario desde "Regístrate"; no hay usuarios de ejemplo.

Si los puertos 5432, 8080 o 3000 ya están ocupados en tu equipo, cámbialos en `.env` con `DB_HOST_PORT`, `APP_PORT` y `WEB_PORT`.

## Ejecución en local (sin Docker para la aplicación)

```bash
docker compose up -d postgres
JWT_SECRET=un-secreto-de-al-menos-32-caracteres ./mvnw spring-boot:run
```

En Windows usa `mvnw.cmd` y define la variable de entorno antes.

Y en otra terminal, el frontend (en http://localhost:5173; Vite reenvía `/api` al backend del puerto 8080):

```bash
cd frontend
npm install
npm run dev
```

## Tests

Backend:

```bash
./mvnw verify
```

Ejecuta **110 tests unitarios** (dominio, cálculo de dinero, parseo de CSV, transiciones de estado...) y **150 de integración**. Los de integración arrancan un PostgreSQL real en Docker con Testcontainers, así que necesitan Docker en marcha.

Los unitarios terminan en `Test` y los ejecuta Surefire; los de integración terminan en `IT` y los ejecuta Failsafe.

Frontend:

```bash
cd frontend
npm test          # tests de lib/: URLs de la API, mensajes de error y formato de importes y fechas
npm run lint
npm run build     # incluye la comprobación de tipos
```

El frontend tiene pocos tests automáticos: cubren la lógica que no es de pantalla. Los flujos (registro, cuentas, movimientos, transferencias, anulación, presupuestos) se han comprobado a mano en el navegador, en móvil (375 px), tablet (768 px) y escritorio.

## Integración continua

En cada push y pull request a `main`, GitHub Actions ejecuta los tests del backend (con PostgreSQL real), el lint, el formato, los tests y el build del frontend, y construye las dos imágenes Docker.

## Variables de entorno

| Variable | Obligatoria | Por defecto | Para qué sirve |
|---|---|---|---|
| `JWT_SECRET` | **Sí** | — | Firma de los tokens. Mínimo 32 caracteres |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | No | `localhost`, `5432`, `fincore`, `fincore`, `fincore` | Conexión a PostgreSQL |
| `DB_HOST_PORT`, `APP_PORT`, `WEB_PORT` | No | `5432`, `8080`, `3000` | Puertos publicados por Docker Compose en tu equipo |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000` | Orígenes que pueden llamar a la API desde un navegador |
| `ADMIN_EMAIL` | No | vacío | Email de un usuario ya registrado al que dar el rol de administrador al arrancar |
| `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` | No | `15m`, `30d` | Duración de los tokens |
| `LOGIN_MAX_ATTEMPTS`, `LOGIN_LOCK_DURATION` | No | `5`, `15m` | Bloqueo tras intentos fallidos |
| `JOBS_ENABLED` | No | `true` | Activa las tareas programadas |

## Limitaciones conocidas

- **La web no cubre todo**: objetivos de ahorro, suscripciones, facturas, importación CSV, reglas de categorización, sesiones y analítica detallada solo están en la API (Swagger).
- **Los tokens se guardan en `localStorage`**, porque la API los devuelve en el cuerpo de la respuesta. Un XSS podría leerlos; lo mitigan que el access token dure 15 minutos y que el refresh token se rote en cada uso. Pasar a cookies `httpOnly` cambiaría el contrato de la API.
- **Los emails no se envían**: se escriben en el log (sin el enlace ni el token). Para producción haría falta un servidor SMTP.
- **El límite de peticiones se guarda en memoria**: con varias instancias de la aplicación, cada una llevaría su propia cuenta.
- **Los tipos de cambio los introduce un administrador**: no se descargan de ningún servicio externo.
- **`/actuator/prometheus` es público** para que Prometheus pueda leerlo. Solo expone contadores agregados, sin datos de usuarios; en producción debería quedar accesible solo desde la red interna.
