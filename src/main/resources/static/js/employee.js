const DAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
const DAY_OF_WEEK_LABELS = {
    MONDAY: "lunes", TUESDAY: "martes", WEDNESDAY: "miércoles", THURSDAY: "jueves",
    FRIDAY: "viernes", SATURDAY: "sábado", SUNDAY: "domingo"
};

let ownEmployeeId = null;
/** Semana de quién se está viendo: igual a ownEmployeeId salvo que un MANAGER elija a otra persona. */
let viewedEmployeeId = null;
let isManagerUser = false;
let isGuestUser = false;
let currentShiftTemplateLabelsById = new Map();

/** Cookie XSRF-TOKEN (legible por JS) que Spring Security espera de vuelta en X-XSRF-TOKEN. */
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

function setStatusMessage(message, isError) {
    const el = document.getElementById("status-message");
    if (!message) {
        el.hidden = true;
        return;
    }
    el.textContent = message;
    el.className = isError ? "status-message error" : "status-message success";
    el.hidden = false;
}

/** Lunes de la semana ISO indicada, calculado sin depender de la fecha actual. */
function mondayOfIsoWeek(isoYear, isoWeek) {
    const jan4 = new Date(Date.UTC(isoYear, 0, 4));
    const jan4Weekday = (jan4.getUTCDay() + 6) % 7; // 0 = lunes
    const week1Monday = new Date(jan4);
    week1Monday.setUTCDate(jan4.getUTCDate() - jan4Weekday);
    const target = new Date(week1Monday);
    target.setUTCDate(week1Monday.getUTCDate() + (isoWeek - 1) * 7);
    return target;
}

function toIsoDateString(date) {
    return date.toISOString().slice(0, 10);
}

function buildWeekDays(isoYear, isoWeek) {
    const monday = mondayOfIsoWeek(isoYear, isoWeek);
    const days = [];
    for (let i = 0; i < 7; i++) {
        const date = new Date(monday);
        date.setUTCDate(monday.getUTCDate() + i);
        const isoDate = toIsoDateString(date);
        days.push({ date: isoDate, label: `${DAY_LABELS[i]} ${date.getUTCDate()}` });
    }
    return days;
}

/** Semana ISO (año+número) a la que pertenece una fecha cualquiera, para poder navegar semana a semana. */
function isoYearWeekOfDate(inputDate) {
    const target = new Date(Date.UTC(inputDate.getUTCFullYear(), inputDate.getUTCMonth(), inputDate.getUTCDate()));
    const dayNumber = (target.getUTCDay() + 6) % 7;
    target.setUTCDate(target.getUTCDate() - dayNumber + 3);
    const firstThursday = new Date(Date.UTC(target.getUTCFullYear(), 0, 4));
    const firstThursdayDayNumber = (firstThursday.getUTCDay() + 6) % 7;
    firstThursday.setUTCDate(firstThursday.getUTCDate() - firstThursdayDayNumber + 3);
    const isoWeek = 1 + Math.round((target - firstThursday) / (7 * 24 * 60 * 60 * 1000));
    return { isoYear: target.getUTCFullYear(), isoWeek };
}

function currentIsoYearWeek() {
    return isoYearWeekOfDate(new Date());
}

/** delta=-1 semana anterior, delta=1 semana siguiente. */
function shiftIsoWeek(isoYear, isoWeek, delta) {
    const monday = mondayOfIsoWeek(isoYear, isoWeek);
    monday.setUTCDate(monday.getUTCDate() + delta * 7);
    return isoYearWeekOfDate(monday);
}

/** "08:00:00" -> "08:00" */
function formatTime(localTime) {
    return localTime.slice(0, 5);
}

/** "MAÑANA" + [08:00-16:00] -> "MAÑANA · 08:00–16:00"; varios tramos se unen con " y ". */
function formatShiftLabel(shiftTemplate) {
    const segments = shiftTemplate.segments
        .map((s) => `${formatTime(s.startTime)}–${formatTime(s.endTime)}`)
        .join(" y ");
    return `${shiftTemplate.name} · ${segments}`;
}

async function loadShiftTemplatesForVenue(venueId) {
    const all = await fetchJson("/api/shift-templates");
    return all.filter((t) => t.venueId === venueId);
}

function renderShiftSelectOptions(shiftTemplates) {
    const select = document.getElementById("pref-shift-input");
    select.innerHTML = "";
    shiftTemplates.forEach((shiftTemplate) => {
        const option = document.createElement("option");
        option.value = String(shiftTemplate.id);
        option.textContent = formatShiftLabel(shiftTemplate);
        select.appendChild(option);
    });
}

/** status es null cuando todavía no se ha generado un cuadrante para esa semana. */
function renderMyWeek(employee, days, assignmentsByDate, status) {
    const heading = document.getElementById("employee-heading");
    heading.hidden = false;
    heading.textContent = status ? `${employee.name} — estado: ${status}` : `${employee.name} — cuadrante no generado todavía`;

    const headRow = document.getElementById("week-head-row");
    const bodyRow = document.getElementById("week-body-row");
    headRow.innerHTML = "";
    bodyRow.innerHTML = "";

    days.forEach((day) => {
        const th = document.createElement("th");
        th.textContent = day.label;
        headRow.appendChild(th);

        const td = document.createElement("td");
        if (!status) {
            td.textContent = "—";
        } else {
            const shiftTemplateId = assignmentsByDate.get(day.date);
            td.textContent = shiftTemplateId ? currentShiftTemplateLabelsById.get(shiftTemplateId) : "Libre";
        }
        bodyRow.appendChild(td);
    });
}

function describePreference(preference) {
    switch (preference.type) {
        case "PREFERS_DAY":
            return `Prefiere trabajar los ${DAY_OF_WEEK_LABELS[preference.dayOfWeek]} (peso ${preference.weight})`;
        case "AVOIDS_DAY":
            return `Evita trabajar los ${DAY_OF_WEEK_LABELS[preference.dayOfWeek]} (peso ${preference.weight})`;
        case "PREFERS_SHIFT":
            return `Prefiere el turno ${currentShiftTemplateLabelsById.get(preference.shiftTemplateId) || preference.shiftTemplateId} (peso ${preference.weight})`;
        case "AVOIDS_SHIFT":
            return `Evita el turno ${currentShiftTemplateLabelsById.get(preference.shiftTemplateId) || preference.shiftTemplateId} (peso ${preference.weight})`;
        case "UNAVAILABLE":
            return `No disponible el ${preference.specificDate}`;
        default:
            return preference.type;
    }
}

function renderPreferences(preferences) {
    const list = document.getElementById("preferences-list");
    list.innerHTML = "";

    if (preferences.length === 0) {
        const empty = document.createElement("li");
        empty.textContent = "Todavía no tienes preferencias guardadas.";
        list.appendChild(empty);
        return;
    }

    preferences.forEach((preference) => {
        const item = document.createElement("li");

        const text = document.createElement("span");
        text.textContent = describePreference(preference);
        item.appendChild(text);

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "preference-delete-button";
        deleteButton.textContent = "Eliminar";
        deleteButton.addEventListener("click", async () => {
            try {
                await fetchJson(`/api/preferences/${preference.id}`, { method: "DELETE" });
                item.remove();
                if (list.children.length === 0) {
                    renderPreferences([]);
                }
            } catch (error) {
                setStatusMessage(error.message, true);
            }
        });
        item.appendChild(deleteButton);

        list.appendChild(item);
    });
}

async function loadAndRenderPreferences(employeeId) {
    const preferences = await fetchJson(`/api/preferences?employeeId=${employeeId}`);
    renderPreferences(preferences);
}

function updatePreferenceFormVisibility() {
    const type = document.getElementById("pref-type-input").value;
    document.getElementById("pref-day-field").hidden = !(type === "PREFERS_DAY" || type === "AVOIDS_DAY");
    document.getElementById("pref-shift-field").hidden = !(type === "PREFERS_SHIFT" || type === "AVOIDS_SHIFT");
    document.getElementById("pref-date-field").hidden = type !== "UNAVAILABLE";
    document.getElementById("pref-weight-field").hidden = type === "UNAVAILABLE";
}

/**
 * Ver tu propia semana: además de fichar, puedes editar tus preferencias.
 * Ver la semana de otra persona (solo MANAGER): cuadrante en solo lectura, sin fichar ni
 * preferencias — no tiene sentido ficharle a otro ni tocar sus preferencias desde aquí.
 */
function updateSectionVisibilityForViewedEmployee() {
    const viewingSelf = viewedEmployeeId === ownEmployeeId;
    document.getElementById("timeclock-section").hidden = !viewingSelf;
    document.getElementById("preferences-section").hidden = !viewingSelf || isGuestUser;
}

async function loadTimeClockStatus() {
    const button = document.getElementById("timeclock-button");
    const status = document.getElementById("timeclock-status");
    try {
        const data = await fetchJson("/api/timeclock/status");
        applyTimeClockStatus(data, button, status);
    } catch (error) {
        status.textContent = "No se pudo cargar el estado de fichaje.";
    }
}

function applyTimeClockStatus(data, button, status) {
    const isClockIn = data.nextAction === "CLOCK_IN";
    button.textContent = isClockIn ? "Fichar entrada" : "Fichar salida";
    button.className = isClockIn ? "btn btn-primary" : "btn btn-teal";
    if (data.lastEntry) {
        const time = new Date(data.lastEntry.timestamp).toLocaleString("es-ES", {
            weekday: "short", hour: "2-digit", minute: "2-digit"
        });
        const lastLabel = data.lastEntry.type === "CLOCK_IN" ? "Entrada" : "Salida";
        status.textContent = `Último fichaje: ${lastLabel} · ${time}`;
    } else {
        status.textContent = "Todavía no has fichado ninguna vez.";
    }
}

async function punch() {
    const button = document.getElementById("timeclock-button");
    const status = document.getElementById("timeclock-status");
    button.disabled = true;
    try {
        await fetchJson("/api/timeclock/punch", { method: "POST" });
        await loadTimeClockStatus();
    } catch (error) {
        status.textContent = error.message;
    } finally {
        button.disabled = false;
    }
}

async function populateEmployeeSelect() {
    const field = document.getElementById("employee-select-field");
    if (!isManagerUser) {
        field.hidden = true;
        return;
    }
    try {
        const employees = await fetchJson("/api/employees");
        const select = document.getElementById("employee-select");
        select.innerHTML = "";
        employees.filter((e) => e.active).forEach((employee) => {
            const option = document.createElement("option");
            option.value = String(employee.id);
            option.textContent = employee.id === ownEmployeeId ? `${employee.name} (tú)` : employee.name;
            select.appendChild(option);
        });
        select.value = String(ownEmployeeId);
        field.hidden = false;
    } catch (error) {
        field.hidden = true;
    }
}

async function loadMyWeek(isoYear, isoWeek) {
    setStatusMessage(null);
    try {
        const employee = await fetchJson(`/api/employees/${viewedEmployeeId}`);
        const shiftTemplates = await loadShiftTemplatesForVenue(employee.venueId);
        currentShiftTemplateLabelsById = new Map(shiftTemplates.map((t) => [t.id, formatShiftLabel(t)]));
        renderShiftSelectOptions(shiftTemplates);

        document.getElementById("preference-fieldset").disabled = false;
        document.getElementById("preference-hint").hidden = true;

        const days = buildWeekDays(isoYear, isoWeek);
        try {
            const schedule = await fetchJson(`/api/schedules?venueId=${employee.venueId}&isoYear=${isoYear}&isoWeek=${isoWeek}`);
            const assignmentsByDate = new Map(
                schedule.assignments.filter((a) => a.employeeId === viewedEmployeeId).map((a) => [a.date, a.shiftTemplateId])
            );
            renderMyWeek(employee, days, assignmentsByDate, schedule.status);
        } catch (scheduleError) {
            renderMyWeek(employee, days, new Map(), null);
            setStatusMessage(`Aún no hay cuadrante generado para esa semana (${scheduleError.message}).`, true);
        }

        updateSectionVisibilityForViewedEmployee();
        if (viewedEmployeeId === ownEmployeeId && !isGuestUser) {
            await loadAndRenderPreferences(ownEmployeeId);
        }
        if (viewedEmployeeId === ownEmployeeId) {
            await loadTimeClockStatus();
        }
    } catch (error) {
        setStatusMessage(error.message, true);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const { isoYear, isoWeek } = currentIsoYearWeek();
    document.getElementById("iso-year-input").value = isoYear;
    document.getElementById("iso-week-input").value = isoWeek;
    updatePreferenceFormVisibility();

    document.getElementById("pref-type-input").addEventListener("change", updatePreferenceFormVisibility);

    document.getElementById("wk-prev").addEventListener("click", () => {
        const current = shiftIsoWeek(
            Number(document.getElementById("iso-year-input").value),
            Number(document.getElementById("iso-week-input").value), -1);
        document.getElementById("iso-year-input").value = current.isoYear;
        document.getElementById("iso-week-input").value = current.isoWeek;
        loadMyWeek(current.isoYear, current.isoWeek);
    });

    document.getElementById("wk-next").addEventListener("click", () => {
        const current = shiftIsoWeek(
            Number(document.getElementById("iso-year-input").value),
            Number(document.getElementById("iso-week-input").value), 1);
        document.getElementById("iso-year-input").value = current.isoYear;
        document.getElementById("iso-week-input").value = current.isoWeek;
        loadMyWeek(current.isoYear, current.isoWeek);
    });

    document.getElementById("timeclock-button").addEventListener("click", punch);

    document.getElementById("employee-select").addEventListener("change", (event) => {
        viewedEmployeeId = Number(event.target.value);
        loadMyWeek(Number(document.getElementById("iso-year-input").value), Number(document.getElementById("iso-week-input").value));
    });

    fetchJson("/api/auth/me").then(async (me) => {
        ownEmployeeId = me.employeeId;
        viewedEmployeeId = me.employeeId;
        isManagerUser = me.role === "MANAGER";
        isGuestUser = !!me.guest;
        const roleLabel = isGuestUser ? "Invitado" : (me.role === "MANAGER" ? "Encargado" : "Empleado");
        document.getElementById("whoami-label").textContent = `${me.name} · ${roleLabel}`;

        // Un empleado o invitado nunca llega al cuadrante (index.html les rebota de vuelta aquí
        // porque no son MANAGER), así que "Volver al cuadrante" no tiene sentido para ellos: el
        // único sitio donde pueden cerrar sesión es este botón.
        if (!isManagerUser) {
            const link = document.getElementById("back-or-logout-link");
            document.getElementById("back-or-logout-label").textContent = "Cerrar sesión";
            link.removeAttribute("href");
            link.style.cursor = "pointer";
            link.setAttribute("role", "button");
            link.setAttribute("tabindex", "0");
            link.addEventListener("click", (event) => {
                event.preventDefault();
                fetchJson("/logout", { method: "POST" })
                    .catch(() => {})
                    .finally(() => { window.location.href = "/"; });
            });
            link.addEventListener("keydown", (event) => {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    link.click();
                }
            });
        }

        await populateEmployeeSelect();
        return loadMyWeek(Number(document.getElementById("iso-year-input").value), Number(document.getElementById("iso-week-input").value));
    }).catch((error) => setStatusMessage(error.message, true));

    document.getElementById("lookup-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const isoYear = Number(document.getElementById("iso-year-input").value);
        const isoWeek = Number(document.getElementById("iso-week-input").value);
        await loadMyWeek(isoYear, isoWeek);
    });

    document.getElementById("preference-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!ownEmployeeId) {
            setStatusMessage("Consulta tu semana primero.", true);
            return;
        }

        const type = document.getElementById("pref-type-input").value;
        const isDayType = type === "PREFERS_DAY" || type === "AVOIDS_DAY";
        const isShiftType = type === "PREFERS_SHIFT" || type === "AVOIDS_SHIFT";
        const isUnavailable = type === "UNAVAILABLE";

        const body = {
            employeeId: ownEmployeeId,
            type,
            dayOfWeek: isDayType ? document.getElementById("pref-day-input").value : null,
            shiftTemplateId: isShiftType ? Number(document.getElementById("pref-shift-input").value) : null,
            specificDate: isUnavailable ? document.getElementById("pref-date-input").value : null,
            weight: isUnavailable ? null : Number(document.getElementById("pref-weight-input").value)
        };

        try {
            await fetchJson("/api/preferences", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage("Preferencia añadida.", false);
            await loadAndRenderPreferences(ownEmployeeId);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });
});
