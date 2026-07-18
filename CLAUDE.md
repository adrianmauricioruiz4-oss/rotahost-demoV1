# CLAUDE.md — Turnos (gestor de cuadrantes para hostelería)

Este fichero es el contrato de trabajo entre el desarrollador y Claude Code.
Léelo entero antes de tocar nada.

---

## 1. Qué estamos construyendo

Una aplicación web que genera cuadrantes semanales rotativos para negocios de hostelería
(bares, restaurantes) de **8 a 15 empleados**, teniendo en cuenta las preferencias de días
y franjas horarias de cada persona.

**Filosofía del producto: copiloto, no piloto automático.**
El sistema *propone* un cuadrante. El encargado lo revisa, lo edita y lo publica.
Nunca se publica nada sin intervención humana. Esta decisión no se discute ni se
"optimiza" en fases posteriores sin aprobación explícita.

**Anti-objetivos (no hagas esto):**
- No usar un LLM para generar el cuadrante. La asignación es **determinista**, en Java.
  Los LLM fallan en satisfacción de restricciones y un fallo aquí es un incumplimiento
  del convenio, no un bug cosmético.
- No meter Timefold/OptaPlanner/OR-Tools en la V1. Para 15 empleados sobra un greedy.
- No microservicios, no Docker Compose de 6 contenedores, no Kafka. Un monolito.
- No React. Vanilla JS.

---

## 2. Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1.0 (Web, Data JPA, Validation) |
| BD | MySQL 8 (H2 en memoria para tests) |
| Frontend | HTML + CSS + JavaScript vanilla, servido desde `src/main/resources/static` |
| Tests | JUnit 5 + AssertJ |
| Build | Maven wrapper (`./mvnw`) |

---

## 3. Reglas de trabajo (IMPORTANTES)

1. **Propón un plan antes de escribir código.** Para cada tarea del roadmap: expón qué
   ficheros vas a crear/tocar y espera confirmación. No empieces a generar código a lo loco.
2. **Una tarea del roadmap = una sesión de trabajo = un commit.** No agrupes tareas.
3. **Ejecuta `./mvnw compile` después de cada tarea.** Si no compila, arréglalo antes de
   seguir. Si tocaste tests o lógica de dominio, ejecuta `./mvnw test`.
4. **Commit automático al terminar cada tarea**, solo si compila y los tests pasan.
   Formato Conventional Commits, en inglés, imperativo:
   ```
   feat(schedule): add hard constraint validator for rest periods
   fix(employee): prevent duplicate email on update
   test(engine): cover rotation fairness across 4 weeks
   docs(readme): document API endpoints
   chore(deps): bump spring boot to 3.3.2
   ```
   Comando: `git add -A && git commit -m "<mensaje>"`.
   **Nunca hagas `git push` sin que te lo pida.** Nunca hagas `--force`, `reset --hard`,
   ni reescribas historia.
5. **Marca la casilla del roadmap** en este mismo fichero al terminar la tarea, en el
   mismo commit.
6. Si una decisión de diseño no está clara, **pregunta**. No inventes requisitos de negocio.

---

## 4. Arquitectura: package-by-feature

```
com.generador.horarios.proyecto
├── employee/        Employee, EmployeeRepository, EmployeeService, EmployeeController, dto/
├── preference/      Preference, ...
├── shift/           ShiftTemplate, ShiftAssignment, ...
├── schedule/        Schedule, ScheduleService, ScheduleController, ...
│   └── engine/      ScheduleGenerator, ScheduleValidator, ConstraintViolation
├── venue/           Venue (el local), CoverageRequirement
└── shared/          config, excepciones, GlobalExceptionHandler
```

Reglas: los controllers no ven entidades JPA (usa DTOs con records). La lógica vive en
services. `engine` no depende de Spring — Java puro, para poder testearlo sin contexto.

---

## 5. Modelo de dominio (V1)

- **Venue** — el local. Horario de apertura, franjas definidas.
- **Employee** — nombre, email, tipo de contrato (`FULL_TIME` 40h / `PART_TIME` con
  `contractHours`), activo sí/no.
- **ShiftTemplate** — turno tipo: `MAÑANA` (08:00–16:00), `TARDE` (16:00–00:00),
  `PARTIDO` (12:00–16:00 + 20:00–00:00). Configurable por venue.
- **CoverageRequirement** — cuánta gente hace falta por día de semana + franja.
  Ej.: viernes TARDE → 3 personas.
- **Preference** — `employeeId`, tipo (`PREFERS_DAY`, `AVOIDS_DAY`, `PREFERS_SHIFT`,
  `AVOIDS_SHIFT`, `UNAVAILABLE`), valor, peso (1–5). `UNAVAILABLE` es restricción **dura**
  (vacaciones, baja, cita médica); el resto son blandas.
- **Schedule** — un cuadrante semanal. Estado: `DRAFT` → `PUBLISHED`. Semana ISO + año.
- **ShiftAssignment** — employee + fecha + shiftTemplate + scheduleId.

---

## 6. Restricciones

**Duras — si se violan, el cuadrante NO se guarda ni se publica. Nunca.**

| ID | Regla |
|---|---|
| H1 | Mínimo **12 horas** entre el fin de una jornada y el inicio de la siguiente |
| H2 | Mínimo **1,5 días** (36h) de descanso semanal ininterrumpido |
| H3 | Máximo **40h** semanales (o `contractHours` si es parcial) |
| H4 | Máximo **9h** de trabajo efectivo al día |
| H5 | No asignar a alguien marcado `UNAVAILABLE` esa fecha |
| H6 | No asignar dos turnos solapados a la misma persona |
| H7 | Cobertura mínima cumplida en cada franja (si no se puede: se avisa, no se rompe H1–H6) |

Estas restricciones dejalas por defecto, pero crea la opcion para que el usuario tenga permiso para modificarlas



**Blandas — se maximizan, se ponderan, nunca invalidan un cuadrante.**

| ID | Regla |
|---|---|
| S1 | Respetar preferencias de día/franja según su peso |
| S2 | **Equidad**: repartir los turnos "malos" (viernes/sábado noche, domingos, festivos) |
| S3 | Evitar rotación brusca (tarde → mañana al día siguiente aunque cumpla las 12h) |
| S4 | Agrupar los días libres en vez de dispersarlos |

`ScheduleValidator` es **obligatorio** y se ejecuta siempre antes de persistir, venga el
cuadrante de donde venga (generador, edición manual del encargado, import). Devuelve
`List<ConstraintViolation>` con severidad `HARD`/`SOFT`. Si hay alguna `HARD`, se rechaza
con 422 y el mensaje explicando cuál y de quién.

---

## 7. Algoritmo (V1)

Greedy con puntuación y backtracking limitado. Nada de heurísticas exóticas.

```
1. Ordenar (día, franja) por dificultad de cobertura (menos candidatos disponibles primero)
2. Para cada hueco a cubrir:
   a. Candidatos = empleados que NO violan ninguna restricción dura
   b. Puntuar cada candidato:
        + peso de sus preferencias que se cumplen
        - penalización por turnos malos ya acumulados esta semana Y en las 3 anteriores  (equidad)
        - penalización por rotación brusca
        - penalización por desviación de sus horas de contrato
   c. Asignar el de mayor puntuación
   d. Si no hay candidatos: registrar hueco sin cubrir y seguir. NUNCA relajar una dura.
3. Devolver Schedule en DRAFT + lista de huecos sin cubrir + informe de equidad
```

El histórico de las 3 semanas anteriores es lo que hace que la rotación sea justa a lo
largo del tiempo. No lo omitas.

---

## 8. Roadmap

Marca las casillas al completar. Una tarea, un commit.

### Fase 1 — Cimientos
- [x] T1.1 — `./mvnw` init, `pom.xml`, estructura de paquetes, `application.yml` (MySQL + perfil `test` con H2), `.gitignore`
- [x] T1.2 — Entidades JPA + repositorios: Venue, Employee, ShiftTemplate, CoverageRequirement
- [x] T1.3 — CRUD de Employee (service + controller + DTOs + validación + tests)
- [x] T1.4 — CRUD de ShiftTemplate y CoverageRequirement
- [x] T1.5 — `GlobalExceptionHandler` + respuesta de error uniforme
- [x] T1.6 — Seed de datos de demo (un bar, 10 empleados, turnos y coberturas realistas)
- [x] T1.7 — CRUD parcial de Venue (`GET`/`PUT`, sin alta/baja): leer y editar nombre y horario de apertura/cierre

### Fase 2 — El núcleo
- [x] T2.1 — Entidades Preference, Schedule, ShiftAssignment + repositorios
- [x] T2.2 — CRUD de Preference (el empleado gestiona las suyas)
- [x] T2.3 — **`ScheduleValidator`**: H1–H7 + tests exhaustivos. Empieza por aquí, antes que el generador.
- [x] T2.4 — `ScheduleGenerator`: greedy sin equidad ni histórico (que cubra y no viole duras)
- [x] T2.5 — Puntuación de preferencias blandas (S1)
- [x] T2.6 — Equidad con histórico de 3 semanas (S2) + informe de equidad
- [x] T2.7 — Rotación suave y agrupación de libranzas (S3, S4)
- [x] T2.8 — `POST /api/schedules/generate` → devuelve DRAFT + huecos + informe

### Fase 3 — Interfaz
- [x] T3.1 — Vista cuadrante semanal (tabla días × empleados), CSS propio, responsive
- [x] T3.2 — Botón "Generar semana" → pinta el DRAFT
- [x] T3.3 — **Edición manual**: cambiar una asignación revalida al vuelo y avisa en rojo si rompe una dura
- [x] T3.4 — Publicar cuadrante (`DRAFT` → `PUBLISHED`, bloquea edición)
- [x] T3.5 — Vista del empleado: mi semana + gestionar mis preferencias
- [ ] T3.6 — Exportar a PDF/imprimible (el papel de la cocina existe)
- [x] T3.7 — Mostrar horas de entrada/salida de cada turno en el cuadrante semanal
- [x] T3.8 — Pantalla de configuración: editar horario del venue y horas de cada turno

### Fase 4 — Salir al mercado
- [ ] T4.1 — Spring Security: roles `MANAGER` / `EMPLOYEE`
- [ ] T4.2 — Multi-tenant básico por `venueId`
- [ ] T4.3 — Migraciones con Flyway
- [ ] T4.4 — Deploy (Docker + VPS) + `README.md` público

### Backlog (NO empezar sin pedirlo)
- LLM para parsear preferencias en lenguaje natural ("el finde del 20 no puedo, tengo boda")
- LLM para explicar en castellano por qué a Juan le tocó el domingo
- Notificaciones por WhatsApp/email al publicar
- Fichaje / control horario
- Gestión de festivos por CCAA

---

## 9. Definition of Done

Una tarea está terminada cuando:
- [ ] `./mvnw compile` pasa
- [ ] `./mvnw test` pasa (toda tarea de `engine` o service **necesita** tests)
- [ ] Sin warnings nuevos
- [ ] JavaDoc en los métodos públicos de services y del engine
- [ ] Casilla marcada en este fichero
- [ ] Commit hecho con mensaje Conventional Commit

## 10. Contexto legal (no es opinión, son las duras)

Convenio de hostelería + Estatuto de los Trabajadores. Las restricciones H1–H4 salen de
ahí. Si dudas de un número, **pregunta antes de codificar**, no lo estimes. Un cuadrante
mal generado que firma el dueño del bar es un problema suyo con Inspección de Trabajo, y
nuestro problema con él.
