# DESIGN.md — Sistema de diseño

Instrucciones para aplicar la estética a toda la aplicación. **Vinculante.** Si algo no está aquí, no se inventa: se pregunta.

---

## Archivos

```
src/main/resources/static/css/design-system.css   ← hoja de estilos, no modificar sin motivo
src/main/resources/static/components.html          ← referencia visual de todas las clases
```

Cargar la fuente y la hoja en este orden en cada página:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/css/design-system.css">
```

Antes de producción, servir Inter en local en vez de desde Google Fonts.

---

## Regla principal

**Usar las clases existentes. No escribir CSS nuevo.**

Si una pantalla parece necesitar un componente que no está en `components.html`, el orden es:

1. Componer con las clases que ya existen
2. Si de verdad no se puede, añadir la clase nueva a `design-system.css` usando solo tokens (`var(--…)`), nunca valores literales
3. Añadir un ejemplo del componente nuevo a `components.html`

Nunca estilos en línea salvo para posicionamiento puntual (`margin-top`, `max-width`).

---

## Los cinco principios

**1. Un solo botón primario por pantalla.** Todo lo demás es `.btn--secondary` o `.btn--quiet`. Si hay dos negros, la pantalla no tiene una acción clara.

**2. El estado nunca se comunica solo con color.** Cada estado lleva su forma: `.mark--dot` (trabajando), `.mark--bars` (pausa), `.mark--ring` (fuera). Siempre acompañado de texto.

**3. El espacio separa, no las líneas.** Usar `.stack-*` y los tokens de espaciado. Las líneas (`--line`) solo en tablas y separadores estructurales.

**4. Nada por debajo de 14px, ningún gris más claro que `--ink-3`.** Los tres tokens de texto superan 4.5:1 sobre blanco. No introducir grises nuevos.

**5. Objetivo táctil mínimo de 48px.** Todos los botones ya lo cumplen. Si se crea un control nuevo, respetarlo.

---

## Reparto de pantallas

### PWA empleado (`/app`)

Máxima simplicidad. Seis elementos por pantalla como máximo.

| Zona | Clases |
|---|---|
| Login | `.pinpad`, `.pin-display`, `.pin-dot` |
| Estado actual | `.status-block--{working\|paused\|out}` + `.status-line` + `.figure` |
| Acción | `.btn--primary.btn--hero` + `.btn--secondary.btn--block` |
| Mis fichajes | `.timeline`, `.timeline-item` |

Reglas específicas:
- La hora del reloj **no** aparece: el móvil ya la muestra.
- El número grande es el tiempo acumulado, no la hora.
- En estado `paused`, el único botón es "Volver al trabajo". Añadir la nota: *"Para fichar la salida, primero termina la pausa"*.

### Panel admin (`/admin`)

Densidad media. Puede mostrar tablas y varias métricas.

| Zona | Clases |
|---|---|
| Navegación | `.topbar` |
| Cabecera | `h2` + `.btn--primary` |
| Incidencias | `.notice--alert` con `.notice-action` |
| Métricas | `.grid-3` + `.tile` |
| Plantilla | `.table` |
| Correcciones | `.modal` + `.field` |
| Sin datos | `.empty` |

Reglas específicas:
- Las incidencias van **arriba**, antes de las métricas. Nunca escondidas en una fila de la tabla.
- Las acciones son `<button>` reales, nunca `<span>` con aspecto de botón.
- La tabla lleva `<th scope="col">`. La cabecera aparece una sola vez, no repetida por fila.

---

## Lenguaje de la interfaz

- **Frase capitalizada siempre.** "Fichar salida", no "FICHAR SALIDA" ni "Fichar Salida".
- **Verbo primero en las acciones.** "Corregir fichaje", no "Corrección de fichaje".
- **Decir qué pasa, no cómo se llama la función.** "Hacer una pausa", no "Iniciar PAUSA_INICIO".
- **Los errores explican y proponen.** "Ya existe un empleado con este NIF", no "Error de validación".
- **Sin signos de exclamación, sin "por favor", sin "correctamente".**
- **Sin emoji.** Nunca, en ninguna pantalla.

---

## Prohibiciones

No usar en ningún caso:

- Fuentes monoespaciadas
- Texto en mayúsculas con `letter-spacing` amplio
- Gradientes, sombras decorativas, desenfoques, brillos
- Bordes en cada elemento (solo tablas y campos de formulario)
- Colores fuera de los tokens
- Iconos ilustrativos o decorativos
- Animaciones más allá de las transiciones de `.btn`
- `localStorage` o `sessionStorage`
- Frameworks CSS (Bootstrap, Tailwind). El proyecto es vanilla.

---

## Accesibilidad — mínimos no negociables

- `<html lang="es">` en todas las páginas
- Todo `<input>` con su `<label class="label" for="…">`
- Errores con `aria-invalid="true"` y `.error-text` asociado
- Avisos dinámicos con `role="status"`
- Foco visible: ya lo aporta `:focus-visible`, no anularlo nunca
- Jerarquía de encabezados sin saltos (`h1` → `h2` → `h3`)
- Botones solo de icono con `aria-label`
- La app debe ser usable entera con teclado

---

## Portabilidad

Este sistema está pensado para reutilizarse en otras aplicaciones. Por eso:

- Los tokens tienen nombres neutros (`--ink`, `--line`, `--ok`), no de dominio
- Ninguna clase menciona fichajes, jornadas ni empleados salvo `.status-block--*`
- Al arrancar un proyecto nuevo, se copia `design-system.css` tal cual y solo se cambia el contenido

Si se añade una clase específica de este dominio, marcarla con un comentario para saber que no viaja al siguiente proyecto.
