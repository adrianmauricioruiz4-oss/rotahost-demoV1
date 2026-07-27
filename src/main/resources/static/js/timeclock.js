/**
 * Pantalla de fichaje del empleado. Tres estados —trabajando, en pausa, fuera— con un solo
 * botón grande cada uno, más "Hacer una pausa" mientras se está trabajando.
 *
 * En pausa el único botón es "Volver al trabajo": el backend rechaza fichar la salida sin
 * cerrar antes la pausa, así que la pantalla no ofrece un camino que no lleva a ningún sitio.
 */
function csrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

async function fetchJson(url, options) {
    const opts = { ...options };
    const method = (opts.method || "GET").toUpperCase();
    if (method !== "GET" && method !== "HEAD") {
        const token = csrfToken();
        if (token) {
            opts.headers = { ...(opts.headers || {}), "X-XSRF-TOKEN": token };
        }
    }
    const response = await fetch(url, opts);
    if (!response.ok) {
        let message = `Error ${response.status} al llamar a ${url}`;
        try {
            const body = await response.json();
            if (body && body.message) {
                message = body.message;
            }
        } catch (ignored) {
            // El cuerpo de error no era JSON; nos quedamos con el mensaje genérico.
        }
        throw new Error(message);
    }
    if (response.status === 204) {
        return null;
    }
    const text = await response.text();
    if (!text) {
        return null;
    }
    return JSON.parse(text);
}

const PUNCH_LABELS = {
    CLOCK_IN: "Fichar entrada",
    CLOCK_OUT: "Fichar salida",
    BREAK_END: "Volver al trabajo"
};

const EVENT_LABELS = {
    CLOCK_IN: "Entrada",
    BREAK_START: "Pausa",
    BREAK_END: "Vuelta",
    CLOCK_OUT: "Salida"
};

/**
 * 283 -> "4h 43m"; menos de una hora, solo minutos. Cero es "0m" y no una raya: recién
 * fichada la entrada, el contador está a cero, que no es lo mismo que no haber nada.
 */
function formatMinutes(minutes) {
    const total = Math.max(0, minutes || 0);
    const hours = Math.floor(total / 60);
    const rest = total % 60;
    return hours === 0 ? `${rest}m` : `${hours}h ${rest}m`;
}

function formatClock(isoDateTime) {
    return new Date(isoDateTime).toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" });
}

function setStatusMessage(message, kind) {
    showNotice("status-message", message, kind);
}

/** Minutos transcurridos entre dos instantes, para el "Vuelta · 22m" de la línea de tiempo. */
function minutesBetween(from, to) {
    return Math.max(0, Math.round((new Date(to) - new Date(from)) / 60000));
}

function renderTimeline(entries) {
    const container = document.getElementById("timeline-items");
    container.replaceChildren();

    if (entries.length === 0) {
        const empty = document.createElement("p");
        empty.className = "text-caption";
        empty.textContent = "Todavía no has fichado hoy.";
        container.appendChild(empty);
        return;
    }

    let breakStartedAt = null;
    entries.forEach((entry) => {
        const item = document.createElement("div");
        item.className = "timeline-item";

        const time = document.createElement("span");
        time.className = "tl-time";
        time.textContent = formatClock(entry.timestamp);
        item.appendChild(time);

        let label = EVENT_LABELS[entry.type] || entry.type;
        if (entry.type === "BREAK_START") {
            breakStartedAt = entry.timestamp;
        } else if (entry.type === "BREAK_END" && breakStartedAt) {
            label += ` · ${formatMinutes(minutesBetween(breakStartedAt, entry.timestamp))}`;
            breakStartedAt = null;
        }

        const event = document.createElement("span");
        event.className = "tl-event";
        event.textContent = label;
        item.appendChild(event);

        container.appendChild(item);
    });
}

function renderStatus(status) {
    const block = document.getElementById("status-block");
    const mark = document.getElementById("state-mark");
    const stateText = document.getElementById("state-text");
    const figure = document.getElementById("figure");
    const since = document.getElementById("since");
    const totalLabel = document.getElementById("total-label");
    const totalValue = document.getElementById("total-value");
    const primary = document.getElementById("primary-action");
    const breakButton = document.getElementById("break-action");
    const note = document.getElementById("action-note");

    primary.textContent = PUNCH_LABELS[status.nextAction] || "Fichar";
    breakButton.hidden = !status.canStartBreak;

    if (status.state === "WORKING") {
        block.className = "status-block status-block--working";
        mark.className = "mark mark--dot";
        stateText.textContent = "Trabajando";
        figure.textContent = formatMinutes(status.workedTodayMinutes);
        since.textContent = status.since ? `Entraste a las ${formatClock(status.since)}` : "";
        totalLabel.textContent = "Esta semana";
        totalValue.textContent = formatMinutes(status.workedThisWeekMinutes);
        note.textContent = "";
    } else if (status.state === "ON_BREAK") {
        block.className = "status-block status-block--paused";
        mark.className = "mark mark--bars";
        stateText.textContent = "En pausa";
        figure.textContent = formatMinutes(status.breakTodayMinutes);
        since.textContent = status.since ? `Pausa desde las ${formatClock(status.since)}` : "";
        totalLabel.textContent = "Trabajado hoy";
        totalValue.textContent = formatMinutes(status.workedTodayMinutes);
        note.textContent = "Para fichar la salida, primero termina la pausa.";
    } else {
        // Fuera de turno y sin nada hecho hoy, una raya dice más que un "0m".
        block.className = "status-block status-block--out";
        mark.className = "mark mark--ring";
        stateText.textContent = "Fuera de turno";
        const workedToday = status.workedTodayMinutes > 0;
        figure.textContent = workedToday ? formatMinutes(status.workedTodayMinutes) : "—";
        since.textContent = workedToday ? "Trabajado hoy" : "Todavía no has empezado";
        totalLabel.textContent = "Esta semana";
        totalValue.textContent = formatMinutes(status.workedThisWeekMinutes);
        note.textContent = "Se guardará la hora exacta al pulsar.";
    }
}

async function refresh() {
    const [status, entries] = await Promise.all([
        fetchJson("/api/timeclock/status"),
        fetchJson("/api/timeclock/entries")
    ]);
    renderStatus(status);
    renderTimeline(entries);
}

/**
 * @param type      acción concreta, o null para la que toque por orden
 * @param isManager función que dice si además hay que repintar el estado del equipo
 */
async function punch(type, isManager) {
    const buttons = [document.getElementById("primary-action"), document.getElementById("break-action")];
    buttons.forEach((b) => { b.disabled = true; });
    try {
        await fetchJson("/api/timeclock/punch", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ type: type || null })
        });
        setStatusMessage(null);
        await refresh();
        if (isManager && isManager()) {
            await loadTeam();
        }
    } catch (error) {
        setStatusMessage(error.message, "alert");
    } finally {
        buttons.forEach((b) => { b.disabled = false; });
    }
}

const POSITION_LABELS = {
    CAMARERO: "Camarero/a",
    COCINERO: "Cocinero/a",
    AYUDANTE_COCINA: "Ayudante de cocina",
    RESPONSABLE_SALA: "Responsable de sala",
    ENCARGADO: "Encargado/a",
    REPARTIDOR: "Repartidor/a"
};

const STATE_PILLS = {
    WORKING: { pill: "pill pill--ok", mark: "mark mark--dot", label: "Trabajando" },
    ON_BREAK: { pill: "pill pill--warn", mark: "mark mark--bars", label: "En pausa" },
    OFF: { pill: "pill pill--off", mark: "mark mark--ring", label: "Sin fichar" }
};

function formatDayAndClock(isoDateTime) {
    return new Date(isoDateTime).toLocaleString("es-ES", {
        weekday: "long", day: "numeric", month: "long", hour: "2-digit", minute: "2-digit"
    });
}

function formatPositions(positions) {
    if (!positions) {
        return "";
    }
    return positions.split(", ").map((value) => POSITION_LABELS[value] || value).join(", ");
}

/**
 * Cierra a mano una jornada que quedó abierta. No corrige un fichaje existente: crea el que
 * falta, porque nadie llegó a fichar la salida. El motivo es obligatorio.
 */
function openCloseShiftForm(row) {
    const form = document.createElement("form");

    const intro = document.createElement("p");
    intro.className = "text-secondary";
    intro.style.marginBottom = "var(--s-5)";
    intro.textContent = `${row.name} entró el ${formatDayAndClock(row.openShiftSince)} y no fichó la salida. `
        + "Quedará constancia de que esta salida la has anotado tú.";
    form.appendChild(intro);

    const timeField = document.createElement("div");
    timeField.className = "field";
    const timeLabel = document.createElement("label");
    timeLabel.className = "label";
    timeLabel.htmlFor = "close-shift-time";
    timeLabel.textContent = "Hora de salida real";
    const timeInput = document.createElement("input");
    timeInput.className = "input";
    timeInput.type = "datetime-local";
    timeInput.id = "close-shift-time";
    timeInput.required = true;
    timeInput.value = row.openShiftSince.slice(0, 16);
    timeField.appendChild(timeLabel);
    timeField.appendChild(timeInput);
    form.appendChild(timeField);

    const reasonField = document.createElement("div");
    reasonField.className = "field";
    const reasonLabel = document.createElement("label");
    reasonLabel.className = "label";
    reasonLabel.htmlFor = "close-shift-reason";
    reasonLabel.textContent = "Motivo";
    const reasonInput = document.createElement("textarea");
    reasonInput.className = "textarea";
    reasonInput.id = "close-shift-reason";
    reasonInput.placeholder = "Se fue sin fichar la salida";
    // Sin required a propósito: el navegador se adelantaría con su propio globo y se
    // saltaría el aria-invalid + .error-text que pide DESIGN.md. La validación es de aquí.
    reasonInput.setAttribute("aria-describedby", "close-shift-error");
    reasonField.appendChild(reasonLabel);
    reasonField.appendChild(reasonInput);
    form.appendChild(reasonField);

    const errorText = document.createElement("p");
    errorText.className = "error-text";
    errorText.id = "close-shift-error";
    errorText.hidden = true;
    form.appendChild(errorText);

    // El aviso se retira en cuanto se empieza a escribir: no tiene sentido dejarlo puesto
    // mientras la persona está corrigiendo justo eso.
    reasonInput.addEventListener("input", () => {
        reasonInput.removeAttribute("aria-invalid");
        errorText.hidden = true;
    });

    const actions = document.createElement("div");
    actions.className = "row-end";
    actions.style.marginTop = "var(--s-6)";

    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "btn btn--secondary";
    cancel.textContent = "Cancelar";
    cancel.addEventListener("click", closeModal);
    actions.appendChild(cancel);

    const save = document.createElement("button");
    save.type = "submit";
    save.className = "btn btn--primary";
    save.textContent = "Guardar salida";
    actions.appendChild(save);
    form.appendChild(actions);

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!reasonInput.value.trim()) {
            reasonInput.setAttribute("aria-invalid", "true");
            errorText.textContent = "Escribe por qué anotas esta salida.";
            errorText.hidden = false;
            return;
        }
        try {
            await fetchJson("/api/timeclock/entries", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    employeeId: row.employeeId,
                    type: "CLOCK_OUT",
                    timestamp: `${timeInput.value}:00`,
                    reason: reasonInput.value.trim()
                })
            });
            closeModal();
            setStatusMessage(`Jornada de ${row.name} cerrada.`, "ok");
            await loadTeam();
        } catch (error) {
            errorText.textContent = error.message;
            errorText.hidden = false;
        }
    });

    openModal("Cerrar jornada", form);
}

function renderAlerts(rows) {
    const section = document.getElementById("alerts-section");
    const list = document.getElementById("alerts-list");
    list.replaceChildren();

    const open = rows.filter((row) => row.openShiftSince);
    section.hidden = open.length === 0;
    if (open.length === 0) {
        return;
    }

    open.forEach((row) => {
        const notice = document.createElement("div");
        notice.className = "notice notice--alert";
        notice.setAttribute("role", "status");

        const mark = document.createElement("span");
        mark.className = "mark mark--dot";
        notice.appendChild(mark);

        const text = document.createElement("span");
        text.textContent = `${row.name} no cerró su jornada del ${formatDayAndClock(row.openShiftSince)}`;
        notice.appendChild(text);

        const action = document.createElement("button");
        action.type = "button";
        action.className = "btn btn--danger btn--sm notice-action";
        action.textContent = "Corregir";
        action.addEventListener("click", () => openCloseShiftForm(row));
        notice.appendChild(action);

        list.appendChild(notice);
    });
}

/** El nombre lo escribe el encargado al dar de alta: texto ajeno, siempre con textContent. */
function buildStaffRow(row) {
    const tr = document.createElement("tr");

    const nameCell = document.createElement("td");
    const name = document.createElement("div");
    name.className = "cell-title";
    name.textContent = row.name;
    nameCell.appendChild(name);
    const sub = document.createElement("div");
    sub.className = "cell-sub";
    sub.textContent = row.openShiftSince
        ? "Jornada anterior sin cerrar"
        : formatPositions(row.positions);
    nameCell.appendChild(sub);
    tr.appendChild(nameCell);

    const entryCell = document.createElement("td");
    if (row.clockedInAt && !row.openShiftSince) {
        entryCell.textContent = formatClock(row.clockedInAt);
    } else {
        entryCell.textContent = "—";
        entryCell.className = "cell-empty";
    }
    tr.appendChild(entryCell);

    const workedCell = document.createElement("td");
    if (row.workedTodayMinutes > 0) {
        workedCell.textContent = formatMinutes(row.workedTodayMinutes);
    } else {
        workedCell.textContent = "—";
        workedCell.className = "cell-empty";
    }
    tr.appendChild(workedCell);

    const stateCell = document.createElement("td");
    stateCell.className = "right";
    if (row.openShiftSince) {
        const fix = document.createElement("button");
        fix.type = "button";
        fix.className = "pill pill--alert";
        fix.textContent = "Corregir";
        fix.addEventListener("click", () => openCloseShiftForm(row));
        stateCell.appendChild(fix);
    } else {
        const style = STATE_PILLS[row.state] || STATE_PILLS.OFF;
        const pill = document.createElement("span");
        pill.className = style.pill;
        const mark = document.createElement("span");
        mark.className = style.mark;
        pill.appendChild(mark);
        pill.appendChild(document.createTextNode(style.label));
        stateCell.appendChild(pill);
    }
    tr.appendChild(stateCell);

    return tr;
}

function renderTeam(overview) {
    document.getElementById("today-label").textContent =
        new Date(`${overview.date}T00:00:00`).toLocaleDateString("es-ES", {
            weekday: "long", day: "numeric", month: "long"
        });

    document.getElementById("count-working").textContent = overview.working;
    document.getElementById("count-break").textContent = overview.onBreak;
    document.getElementById("count-off").textContent = overview.notClockedIn;

    renderAlerts(overview.staff);

    const list = document.getElementById("staff-list");
    const table = document.getElementById("staff-table");
    const empty = document.getElementById("staff-empty");
    list.replaceChildren();

    const isEmpty = overview.staff.length === 0;
    table.hidden = isEmpty;
    empty.hidden = !isEmpty;
    overview.staff.forEach((row) => list.appendChild(buildStaffRow(row)));
}

async function loadTeam() {
    renderTeam(await fetchJson("/api/timeclock/overview"));
}

/* ---------- arranque ---------- */

async function refreshAll(isManager) {
    await refresh();
    if (isManager) {
        await loadTeam();
    }
}

document.addEventListener("DOMContentLoaded", () => {
    let isManager = false;

    document.getElementById("primary-action").addEventListener("click", () => punch(null, () => isManager));
    document.getElementById("break-action").addEventListener("click", () => punch("BREAK_START", () => isManager));
    document.getElementById("refresh-button").addEventListener("click", () => {
        loadTeam().catch((error) => setStatusMessage(error.message, "alert"));
    });

    fetchJson("/api/auth/me")
        .then((me) => {
            // El nombre lo escribe el encargado al dar de alta: texto ajeno, nunca como HTML.
            document.getElementById("greeting").textContent = `Hola, ${me.name.split(" ")[0]}`;
            isManager = me.role === "MANAGER";
            document.getElementById("team-section").hidden = !isManager;
            return refreshAll(isManager);
        })
        .catch((error) => setStatusMessage(error.message, "alert"));
});
