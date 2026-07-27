# RotaTeam

Generador de cuadrantes semanales para bares y restaurantes de 8 a 15 empleados. El sistema
*propone* un cuadrante a partir de las preferencias del equipo y las necesidades de cobertura del
local; el encargado lo revisa, lo edita y lo publica. Nunca se publica nada sin intervención
humana — no hay generación por IA, la asignación es determinista.

## Stack

- Java 21, Spring Boot 4.1 (Web, Data JPA, Security, Validation)
- MySQL 8 en producción, H2 en memoria para desarrollo local y tests
- Flyway para las migraciones de esquema
- Frontend: HTML + CSS + JavaScript vanilla, sin build step, servido desde `src/main/resources/static`

## Desarrollo local

No hace falta MySQL para desarrollar: el perfil por defecto (`dev`) usa H2 en memoria.

```bash
./mvnw spring-boot:run
```

La app queda en `http://localhost:8080`. Al arrancar por primera vez se siembra un bar de demo
con 10 empleados; las credenciales de acceso quedan en el log de arranque
(`Datos de demo cargados. Login: ...`). Por defecto: cualquier email de empleado sembrado +
contraseña `demo1234` (uno de ellos es el encargado, el resto son empleados normales).

```bash
./mvnw test
```

## Desplegar con Docker

Pensado para un único VPS con Docker y Docker Compose instalados — dos contenedores (app +
MySQL), sin microservicios.

```bash
cp .env.example .env   # y cambia DB_PASSWORD
docker compose up -d --build
```

La primera vez, Flyway crea el esquema y el arranque siembra el bar de demo igual que en local
(bórralo a mano si vas a usarlo en producción real). La app queda expuesta en el puerto 8080 del
host.

### Notas para producción real

- **HTTPS**: `docker-compose.yml` no incluye TLS. Pon un reverse proxy delante (Caddy o nginx +
  certbot) que termine HTTPS y redirija al puerto 8080 del contenedor `app`.
- **Contraseña de MySQL**: `.env` nunca debe subirse al repo (ya está en `.gitignore`); usa una
  contraseña generada, no la de `.env.example`.
- **Backups**: el volumen `db-data` persiste los datos entre reinicios del contenedor, pero no es
  un backup. Añade un `mysqldump` periódico si vas a usarlo en serio.
- **Variables de entorno** que acepta la app (ver `application.yml`): `DB_HOST`, `DB_PORT`,
  `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`. `SPRING_PROFILES_ACTIVE` debe ser distinto de `dev`
  para que use MySQL en vez de H2.
- **Avisos por correo**: vienen desactivados. Sin `MAIL_ENABLED=true` la aplicación no envía
  nada; anota el aviso en el log y se lo dice al encargado en pantalla. Para activarlos:
  `MAIL_ENABLED`, `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME` y `SMTP_PASSWORD`, todas
  en el `.env` (ver `.env.example`). Con Gmail hace falta una *contraseña de aplicación*, no la
  de la cuenta. Ninguna credencial ni dirección real debe escribirse en `application.yml`: ese
  fichero sí va al repositorio.

## Estructura del proyecto

Package-by-feature: `employee`, `preference`, `shift`, `schedule` (con el motor de generación en
`schedule/engine`, sin dependencias de Spring), `venue`, `timeclock` (fichaje, con el cómputo de
jornada en `WorkedTime`, también Java puro), `shared` (config, seguridad, manejo de errores). El
contrato completo de desarrollo — modelo de dominio, restricciones duras/blandas, algoritmo de
generación y roadmap — está en [`CLAUDE.md`](CLAUDE.md).

El frontend es HTML + CSS + JavaScript vanilla en `src/main/resources/static`, sobre una única
hoja de estilos, `css/design-system.css`. `components.html` es la referencia viva de todas las
clases disponibles: antes de inventar una clase nueva, mírala ahí.
