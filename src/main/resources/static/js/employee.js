const DAY_LABELS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
const DAY_OF_WEEK_LABELS = {
    MONDAY: "lunes", TUESDAY: "martes", WEDNESDAY: "miércoles", THURSDAY: "jueves",
    FRIDAY: "viernes", SATURDAY: "sábado", SUNDAY: "domingo"
};

let ownEmployeeId = null;
/** Semana de quién se está viendo: igual a ownEmployeeId salvo que un MANAGER elija a otra persona. */
let viewedEmployeeId = null;
let isManagerUser = false;
let isGuestUser = false;
let currentShiftTemplates = [];
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
    showNotice("status-message", message, isError ? "alert" : "ok");
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
        days.push({ date: toIsoDateString(date), label: DAY_LABELS[i], dayNumber: date.getUTCDate() });
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

/**
 * Una fila por día. En vertical y no en horizontal a propósito: siete columnas no caben en
 * un móvil, y esta pantalla se mira sobre todo desde el móvil.
 * status es null cuando todavía no se ha generado un cuadrante para esa semana.
 */
function renderMyWeek(employee, days, assignmentsByDate, status) {
    document.getElementById("employee-heading").textContent = `Turnos de ${employee.name}`;

    const pill = document.getElementById("week-status-pill");
    const mark = document.getElementById("week-status-mark");
    const text = document.getElementById("week-status-text");
    pill.hidden = false;
    if (status === "PUBLISHED") {
        pill.className = "pill pill--ok";
        mark.className = "mark mark--dot";
        text.textContent = "Publicado";
    } else if (status === "DRAFT") {
        pill.className = "pill pill--warn";
        mark.className = "mark mark--bars";
        text.textContent = "Borrador, puede cambiar";
    } else {
        pill.className = "pill pill--off";
        mark.className = "mark mark--ring";
        text.textContent = "Sin generar";
    }

    const body = document.getElementById("week-body");
    body.replaceChildren();

    days.forEach((day) => {
        const row = document.createElement("tr");

        const dayCell = document.createElement("td");
        const dayName = document.createElement("div");
        dayName.className = "cell-title";
        dayName.textContent = `${day.label} ${day.dayNumber}`;
        dayCell.appendChild(dayName);
        row.appendChild(dayCell);

        const shiftCell = document.createElement("td");
        if (!status) {
            shiftCell.textContent = "—";
            shiftCell.className = "cell-empty";
        } else {
            const shiftTemplateId = assignmentsByDate.get(day.date);
            if (shiftTemplateId) {
                shiftCell.textContent = currentShiftTemplateLabelsById.get(shiftTemplateId);
            } else {
                shiftCell.textContent = "Libre";
                shiftCell.className = "cell-empty";
            }
        }
        row.appendChild(shiftCell);

        body.appendChild(row);
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
    const empty = document.getElementById("preferences-empty");
    const table = document.getElementById("preferences-table");
    list.replaceChildren();

    const isEmpty = preferences.length === 0;
    empty.hidden = !isEmpty;
    table.hidden = isEmpty;
    if (isEmpty) {
        return;
    }

    preferences.forEach((preference) => {
        const row = document.createElement("tr");

        const textCell = document.createElement("td");
        textCell.textContent = describePreference(preference);
        row.appendChild(textCell);

        const actionsCell = document.createElement("td");
        actionsCell.className = "right";
        const actions = document.createElement("div");
        actions.className = "row-actions";

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "btn btn--quiet btn--sm";
        deleteButton.textContent = "Quitar";
        deleteButton.addEventListener("click", async () => {
            try {
                await fetchJson(`/api/preferences/${preference.id}`, { method: "DELETE" });
                await loadAndRenderPreferences(ownEmployeeId);
            } catch (error) {
                setStatusMessage(error.message, true);
            }
        });
        actions.appendChild(deleteButton);
        actionsCell.appendChild(actions);
        row.appendChild(actionsCell);

        list.appendChild(row);
    });
}

async function loadAndRenderPreferences(employeeId) {
    renderPreferences(await fetchJson(`/api/preferences?employeeId=${employeeId}`));
}

/** Campo con su label asociado por id, como exige la accesibilidad mínima de DESIGN.md. */
function buildField(id, labelText, control) {
    const field = document.createElement("div");
    field.className = "field";
    control.id = id;

    const label = document.createElement("label");
    label.className = "label";
    label.htmlFor = id;
    label.textContent = labelText;

    field.appendChild(label);
    field.appendChild(control);
    return field;
}

/**
 * Alta de preferencia en un modal. Los campos que se piden dependen del tipo: un día de la
 * semana, un turno o una fecha suelta, y el peso solo cuando la preferencia es negociable
 * (UNAVAILABLE es restricción dura, no admite peso).
 */
function openPreferenceForm() {
    const form = document.createElement("form");

    const typeSelect = document.createElement("select");
    typeSelect.className = "select";
    [
        ["PREFERS_DAY", "Prefiero trabajar un día"],
        ["AVOIDS_DAY", "Prefiero no trabajar un día"],
        ["PREFERS_SHIFT", "Prefiero un turno"],
        ["AVOIDS_SHIFT", "Prefiero evitar un turno"],
        ["UNAVAILABLE", "No puedo un día concreto"]
    ].forEach(([value, label]) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label;
        typeSelect.appendChild(option);
    });
    form.appendChild(buildField("pref-type-input", "Qué quieres decir", typeSelect));

    const daySelect = document.createElement("select");
    daySelect.className = "select";
    Object.entries(DAY_OF_WEEK_LABELS).forEach(([value, label]) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label.charAt(0).toUpperCase() + label.slice(1);
        daySelect.appendChild(option);
    });
    const dayField = buildField("pref-day-input", "Día de la semana", daySelect);
    form.appendChild(dayField);

    const shiftSelect = document.createElement("select");
    shiftSelect.className = "select";
    currentShiftTemplates.forEach((shiftTemplate) => {
        const option = document.createElement("option");
        option.value = String(shiftTemplate.id);
        option.textContent = formatShiftLabel(shiftTemplate);
        shiftSelect.appendChild(option);
    });
    const shiftField = buildField("pref-shift-input", "Turno", shiftSelect);
    form.appendChild(shiftField);

    const dateInput = document.createElement("input");
    dateInput.className = "input";
    dateInput.type = "date";
    const dateField = buildField("pref-date-input", "Fecha", dateInput);
    form.appendChild(dateField);

    const weightInput = document.createElement("input");
    weightInput.className = "input";
    weightInput.type = "number";
    weightInput.min = "1";
    weightInput.max = "5";
    weightInput.value = "3";
    const weightField = buildField("pref-weight-input", "Cuánto te importa, de 1 a 5", weightInput);
    form.appendChild(weightField);

    function updateVisibility() {
        const type = typeSelect.value;
        dayField.hidden = !(type === "PREFERS_DAY" || type === "AVOIDS_DAY");
        shiftField.hidden = !(type === "PREFERS_SHIFT" || type === "AVOIDS_SHIFT");
        dateField.hidden = type !== "UNAVAILABLE";
        weightField.hidden = type === "UNAVAILABLE";
    }
    typeSelect.addEventListener("change", updateVisibility);
    updateVisibility();

    const actions = document.createElement("div");
    actions.className = "row-end";
    actions.style.marginTop = "var(--s-6)";

    const cancelButton = document.createElement("button");
    cancelButton.type = "button";
    cancelButton.className = "btn btn--secondary";
    cancelButton.textContent = "Cancelar";
    cancelButton.addEventListener("click", closeModal);
    actions.appendChild(cancelButton);

    const saveButton = document.createElement("button");
    saveButton.type = "submit";
    saveButton.className = "btn btn--primary";
    saveButton.textContent = "Guardar";
    actions.appendChild(saveButton);
    form.appendChild(actions);

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const type = typeSelect.value;
        const isDayType = type === "PREFERS_DAY" || type === "AVOIDS_DAY";
        const isShiftType = type === "PREFERS_SHIFT" || type === "AVOIDS_SHIFT";
        const isUnavailable = type === "UNAVAILABLE";

        const body = {
            employeeId: ownEmployeeId,
            type,
            dayOfWeek: isDayType ? daySelect.value : null,
            shiftTemplateId: isShiftType ? Number(shiftSelect.value) : null,
            specificDate: isUnavailable ? dateInput.value : null,
            weight: isUnavailable ? null : Number(weightInput.value)
        };

        try {
            await fetchJson("/api/preferences", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            closeModal();
            setStatusMessage("Preferencia guardada.", false);
            await loadAndRenderPreferences(ownEmployeeId);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    openModal("Añadir preferencia", form);
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

const EVENT_LABELS = {
    CLOCK_IN: "Entrada",
    BREAK_START: "Pausa",
    BREAK_END: "Vuelta de la pausa",
    CLOCK_OUT: "Salida"
};

/** Solo el resumen del último fichaje: fichar de verdad se hace en fichajes.html. */
async function loadTimeClockStatus() {
    const status = document.getElementById("timeclock-status");
    try {
        const data = await fetchJson("/api/timeclock/status");
        if (data.lastEntry) {
            const time = new Date(data.lastEntry.timestamp).toLocaleString("es-ES", {
                weekday: "long", hour: "2-digit", minute: "2-digit"
            });
            status.textContent = `Tu último fichaje: ${EVENT_LABELS[data.lastEntry.type] || data.lastEntry.type}, ${time}.`;
        } else {
            status.textContent = "Todavía no has fichado ninguna vez.";
        }
    } catch (error) {
        status.textContent = "No se ha podido cargar tu estado de fichaje.";
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
        select.replaceChildren();
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
        currentShiftTemplates = await loadShiftTemplatesForVenue(employee.venueId);
        currentShiftTemplateLabelsById = new Map(currentShiftTemplates.map((t) => [t.id, formatShiftLabel(t)]));

        const days = buildWeekDays(isoYear, isoWeek);
        try {
            const schedule = await fetchJson(`/api/schedules?venueId=${employee.venueId}&isoYear=${isoYear}&isoWeek=${isoWeek}`);
            const assignmentsByDate = new Map(
                schedule.assignments.filter((a) => a.employeeId === viewedEmployeeId).map((a) => [a.date, a.shiftTemplateId])
            );
            renderMyWeek(employee, days, assignmentsByDate, schedule.status);
        } catch (scheduleError) {
            renderMyWeek(employee, days, new Map(), null);
            setStatusMessage("Esa semana todavía no tiene cuadrante.", true);
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

function currentInputWeek() {
    return {
        isoYear: Number(document.getElementById("iso-year-input").value),
        isoWeek: Number(document.getElementById("iso-week-input").value)
    };
}

function applyWeekToInputs(week) {
    document.getElementById("iso-year-input").value = week.isoYear;
    document.getElementById("iso-week-input").value = week.isoWeek;
}

document.addEventListener("DOMContentLoaded", () => {
    applyWeekToInputs(currentIsoYearWeek());

    document.getElementById("wk-prev").addEventListener("click", () => {
        const { isoYear, isoWeek } = currentInputWeek();
        const week = shiftIsoWeek(isoYear, isoWeek, -1);
        applyWeekToInputs(week);
        loadMyWeek(week.isoYear, week.isoWeek);
    });

    document.getElementById("wk-next").addEventListener("click", () => {
        const { isoYear, isoWeek } = currentInputWeek();
        const week = shiftIsoWeek(isoYear, isoWeek, 1);
        applyWeekToInputs(week);
        loadMyWeek(week.isoYear, week.isoWeek);
    });

    document.getElementById("new-preference-button").addEventListener("click", openPreferenceForm);

    document.getElementById("employee-select").addEventListener("change", (event) => {
        viewedEmployeeId = Number(event.target.value);
        const { isoYear, isoWeek } = currentInputWeek();
        loadMyWeek(isoYear, isoWeek);
    });

    document.getElementById("lookup-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const { isoYear, isoWeek } = currentInputWeek();
        await loadMyWeek(isoYear, isoWeek);
    });

    fetchJson("/api/auth/me").then(async (me) => {
        ownEmployeeId = me.employeeId;
        viewedEmployeeId = me.employeeId;
        isManagerUser = me.role === "MANAGER";
        isGuestUser = !!me.guest;

        // La identidad, la navegación según rol y el "Cerrar sesión" los pinta la barra
        // superior compartida (shell.js), igual que en el resto de vistas.

        await populateEmployeeSelect();
        const { isoYear, isoWeek } = currentInputWeek();
        return loadMyWeek(isoYear, isoWeek);
    }).catch((error) => setStatusMessage(error.message, true));
});
