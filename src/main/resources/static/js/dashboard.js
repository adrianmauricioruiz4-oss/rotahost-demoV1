/** Panel principal (T4.6): resumen del venue propio tras el login. */
let currentVenueId = null;

async function fetchJson(url, options) {
    const response = await fetch(url, options);
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

function setStatusMessage(message, isError) {
    showNotice("status-message", message, isError ? "alert" : "ok");
}

/**
 * Estado del cuadrante como píldora. Cada estado lleva su forma además del color,
 * porque el color solo no comunica el estado.
 */
function renderScheduleStatus(scheduleStatus) {
    const badge = document.getElementById("dash-status-badge");
    const mark = document.getElementById("dash-status-mark");
    const text = document.getElementById("dash-status-text");
    const hint = document.getElementById("dash-schedule-hint");

    if (scheduleStatus === "PUBLISHED") {
        badge.className = "pill pill--ok";
        mark.className = "mark mark--dot";
        text.textContent = "Publicado";
        hint.textContent = "El equipo ya puede consultarlo. Los cambios de última hora se hacen desde el cuadrante.";
    } else if (scheduleStatus === "DRAFT") {
        badge.className = "pill pill--warn";
        mark.className = "mark mark--bars";
        text.textContent = "Borrador";
        hint.textContent = "Hay una propuesta sin publicar. Revísala y publícala cuando te encaje.";
    } else {
        badge.className = "pill pill--off";
        mark.className = "mark mark--ring";
        text.textContent = "Sin generar";
        hint.textContent = "Esta semana todavía no tiene cuadrante.";
    }
    document.getElementById("dash-schedule-section").hidden = false;
}

/**
 * Un aviso por conflicto, arriba del todo. El texto lo compone el backend a partir de
 * las restricciones incumplidas, así que se inserta con textContent y nunca como HTML.
 */
function renderAlerts(alerts) {
    const section = document.getElementById("dash-alerts-section");
    const list = document.getElementById("dash-alerts-list");
    list.replaceChildren();

    const count = alerts ? alerts.length : 0;
    document.getElementById("dash-alerts-count").textContent = String(count);
    document.getElementById("dash-alerts-tile").className = count > 0 ? "tile tile-warn" : "tile";

    if (count === 0) {
        section.hidden = true;
        return;
    }
    section.hidden = false;
    alerts.forEach((alert) => {
        const item = document.createElement("p");
        list.appendChild(item);
        showNotice(item, alert, "alert");
    });
}

/** Lunes de la semana ISO, para poder etiquetar los días del resumen. */
function mondayOfIsoWeek(isoYear, isoWeek) {
    const jan4 = new Date(Date.UTC(isoYear, 0, 4));
    const jan4Weekday = (jan4.getUTCDay() + 6) % 7; // 0 = lunes
    const week1Monday = new Date(jan4);
    week1Monday.setUTCDate(jan4.getUTCDate() - jan4Weekday);
    const target = new Date(week1Monday);
    target.setUTCDate(week1Monday.getUTCDate() + (isoWeek - 1) * 7);
    return target;
}

const DAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

function buildWeekDays(isoYear, isoWeek) {
    const monday = mondayOfIsoWeek(isoYear, isoWeek);
    const days = [];
    for (let i = 0; i < 7; i++) {
        const date = new Date(monday);
        date.setUTCDate(monday.getUTCDate() + i);
        days.push({ date: date.toISOString().slice(0, 10), label: `${DAY_LABELS[i]} ${date.getUTCDate()}` });
    }
    return days;
}

/**
 * El vistazo rápido al cuadrante desde el panel: cuánta gente hay cada día por franja, sin
 * tener que abrir la pantalla del cuadrante. Es lo primero que un encargado quiere saber al
 * entrar. Si esa semana no tiene cuadrante, no se enseña nada: no hay nada que resumir.
 */
async function loadWeekGlance(summary) {
    const wrap = document.getElementById("dash-band-wrap");
    if (!summary.scheduleStatus) {
        wrap.hidden = true;
        return;
    }
    try {
        const [schedule, allTemplates] = await Promise.all([
            fetchJson(`/api/schedules?venueId=${currentVenueId}&isoYear=${summary.isoYear}&isoWeek=${summary.isoWeek}`),
            fetchJson("/api/shift-templates")
        ]);
        const templates = allTemplates.filter((t) => t.venueId === currentVenueId);

        const byEmployee = new Map();
        schedule.assignments.forEach((assignment) => {
            if (!byEmployee.has(assignment.employeeId)) {
                byEmployee.set(assignment.employeeId, new Map());
            }
            byEmployee.get(assignment.employeeId).set(assignment.date, assignment.shiftTemplateId);
        });

        renderBandTable(
            document.getElementById("dash-band-head"),
            document.getElementById("dash-band-body"),
            buildWeekDays(summary.isoYear, summary.isoWeek),
            [...byEmployee.keys()],
            byEmployee,
            (id) => templates.find((t) => t.id === id) || null);
        wrap.hidden = false;
    } catch (error) {
        // El resumen es un extra: si falla, el panel sigue sirviendo para lo demás.
        wrap.hidden = true;
    }
}

async function loadSummary() {
    setStatusMessage(null);
    const summary = await fetchJson("/api/dashboard/summary");

    document.getElementById("dash-venue-name").textContent = summary.venueName;
    document.getElementById("dash-employee-count").textContent = summary.employeeCount;
    document.getElementById("dash-week-number").textContent = summary.isoWeek;
    document.getElementById("dash-week-label").textContent = `Semana del año ${summary.isoYear}`;
    renderScheduleStatus(summary.scheduleStatus);
    renderAlerts(summary.alerts);

    document.getElementById("dash-stats").hidden = false;
    if (typeof window.updateShellVenueName === "function") {
        window.updateShellVenueName(summary.venueName);
    }
    await loadWeekGlance(summary);
}

document.addEventListener("DOMContentLoaded", () => {
    fetchJson("/api/auth/me")
        .then((me) => {
            if (me.role !== "MANAGER") {
                window.location.href = "employee.html";
                return;
            }
            currentVenueId = me.venueId;
            return loadSummary();
        })
        .catch((error) => setStatusMessage(error.message, true));
});
