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

async function punch(type) {
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
    } catch (error) {
        setStatusMessage(error.message, "alert");
    } finally {
        buttons.forEach((b) => { b.disabled = false; });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("primary-action").addEventListener("click", () => punch(null));
    document.getElementById("break-action").addEventListener("click", () => punch("BREAK_START"));

    fetchJson("/api/auth/me")
        .then((me) => {
            // El nombre lo escribe el encargado al dar de alta: texto ajeno, nunca como HTML.
            document.getElementById("greeting").textContent = `Hola, ${me.name.split(" ")[0]}`;
            return refresh();
        })
        .catch((error) => setStatusMessage(error.message, "alert"));
});
