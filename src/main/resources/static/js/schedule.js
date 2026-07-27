const DAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
const MONTH_LABELS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

let currentVenueId = null;
let currentIsoYear = null;
let currentIsoWeek = null;
let currentScheduleId = null;
let currentScheduleStatus = null;
let currentEmployees = [];
let currentShiftTemplates = [];
let currentDays = [];
let assignmentsByEmployeeDate = new Map();
let unavailableSet = new Set();
/** null = no se han recalculado desde la última vez que se generó (ver GET /api/schedules). */
let currentUncoveredSlots = null;
let currentEquityReport = null;
/**
 * Modo "cambios de última hora": el encargado ha pedido expresamente tocar un cuadrante ya
 * publicado. Se apaga al recargar la semana, para que no quede abierto sin querer.
 */
let lastMinuteMode = false;
let currentVenueName = "";

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

function setStatusMessage(message, kind) {
    showNotice("status-message", message, kind);
}

/* ---------- fechas ---------- */

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
        days.push({ date: toIsoDateString(date), label: `${DAY_LABELS[i]} ${date.getUTCDate()}` });
    }
    return days;
}

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

function formatDateRangeLabel(days) {
    const first = new Date(`${days[0].date}T00:00:00Z`);
    const last = new Date(`${days[days.length - 1].date}T00:00:00Z`);
    const firstMonth = MONTH_LABELS[first.getUTCMonth()];
    const lastMonth = MONTH_LABELS[last.getUTCMonth()];
    const year = last.getUTCFullYear();
    if (first.getUTCMonth() === last.getUTCMonth()) {
        return `${first.getUTCDate()} – ${last.getUTCDate()} ${lastMonth} ${year}`;
    }
    return `${first.getUTCDate()} ${firstMonth} – ${last.getUTCDate()} ${lastMonth} ${year}`;
}

function formatDayLabel(isoDate) {
    const day = currentDays.find((d) => d.date === isoDate);
    return day ? day.label : isoDate;
}

/* ---------- formato de turnos ---------- */

function formatTime(localTime) {
    return localTime.slice(0, 5);
}

/** "08:00–16:00"; el partido, con sus dos tramos: "12:00–16:00 y 20:00–00:00". */
function formatSegments(segments) {
    if (!segments || segments.length === 0) {
        return "";
    }
    return segments.map((s) => `${formatTime(s.startTime)}–${formatTime(s.endTime)}`).join(" y ");
}

function shiftTemplateById(id) {
    return currentShiftTemplates.find((t) => t.id === id) || null;
}

/* ---------- carga de datos ---------- */

async function loadReferenceData(venueId) {
    const [employees, allShiftTemplates] = await Promise.all([
        fetchJson("/api/employees"),
        fetchJson("/api/shift-templates")
    ]);
    return {
        employees: employees.filter((e) => e.venueId === venueId && e.active),
        shiftTemplates: allShiftTemplates.filter((t) => t.venueId === venueId)
    };
}

/** Marca (empleado, fecha) como no disponible si hay una preferencia UNAVAILABLE para ese día. */
async function loadUnavailableSet(employeeIds, weekDateSet) {
    const allPreferences = await fetchJson("/api/preferences");
    const employeeIdSet = new Set(employeeIds);
    const set = new Set();
    allPreferences.forEach((preference) => {
        if (preference.type === "UNAVAILABLE" && employeeIdSet.has(preference.employeeId) && weekDateSet.has(preference.specificDate)) {
            set.add(`${preference.employeeId},${preference.specificDate}`);
        }
    });
    return set;
}

function groupAssignments(assignments) {
    const map = new Map();
    assignments.forEach((assignment) => {
        if (!map.has(assignment.employeeId)) {
            map.set(assignment.employeeId, new Map());
        }
        map.get(assignment.employeeId).set(assignment.date, assignment.shiftTemplateId);
    });
    return map;
}

/* ---------- inputs de la semana ---------- */

/** El venue no se elige a mano: es el del encargado autenticado (ver /api/auth/me en el init). */
function getVenueId() {
    return currentVenueId;
}

function getIsoYear() {
    return Number(document.getElementById("iso-year-input").value);
}

function getIsoWeek() {
    return Number(document.getElementById("iso-week-input").value);
}

/* ---------- render: cabecera y rejilla ---------- */

function renderWeekLabel() {
    const rangeLabel = formatDateRangeLabel(currentDays);
    document.getElementById("board-sub").textContent =
        `Semana ${currentIsoWeek} · ${rangeLabel} · ${currentEmployees.length} personas`;
    document.getElementById("print-subtitle").textContent =
        `Semana ${currentIsoWeek}/${currentIsoYear} · ${rangeLabel}`;
}

function renderHead() {
    const headRow = document.getElementById("schedule-head-row");
    headRow.replaceChildren();

    const nameTh = document.createElement("th");
    nameTh.scope = "col";
    nameTh.textContent = "Persona";
    headRow.appendChild(nameTh);

    currentDays.forEach((day) => {
        const th = document.createElement("th");
        th.scope = "col";
        th.textContent = day.label;
        headRow.appendChild(th);
    });
}

/**
 * Cada celda es un botón real, no un div pinchable: así se llega con el tabulador y se
 * activa con Enter. El turno se distingue por su nombre y su horario, nunca por el color.
 */
function buildCell(employee, day) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "rota-cell";
    button.dataset.employeeId = String(employee.id);
    button.dataset.date = day.date;

    const isUnavailable = unavailableSet.has(`${employee.id},${day.date}`);
    const shiftTemplateId = assignmentsByEmployeeDate.get(employee.id)?.get(day.date) || null;
    const shiftTemplate = shiftTemplateId ? shiftTemplateById(shiftTemplateId) : null;

    if (isUnavailable) {
        button.className += " rota-cell--off";
        button.textContent = "No disponible";
        button.disabled = true;
        button.setAttribute("aria-label", `${employee.name}, ${day.label}: no disponible`);
        return button;
    }
    if (!shiftTemplate) {
        button.className += " rota-cell--free";
        button.textContent = "Libre";
        button.setAttribute("aria-label", `${employee.name}, ${day.label}: libre. Cambiar turno`);
    } else {
        // El color va por franja; el nombre del turno sigue escrito, no depende del color.
        button.className += ` rota-cell--${shiftColourOf(shiftTemplate)}`;
        button.textContent = shiftTemplate.name;
        const time = document.createElement("span");
        time.className = "rota-time";
        time.textContent = formatSegments(shiftTemplate.segments);
        button.appendChild(time);
        button.setAttribute("aria-label",
            `${employee.name}, ${day.label}: ${shiftTemplate.name}, ${formatSegments(shiftTemplate.segments)}. Cambiar turno`);
    }

    // Un cuadrante publicado está bloqueado salvo que se haya pedido el modo de última hora.
    button.disabled = currentScheduleStatus === "PUBLISHED" && !lastMinuteMode;
    return button;
}

function renderGrid() {
    const body = document.getElementById("schedule-body");
    body.replaceChildren();

    currentEmployees.forEach((employee) => {
        const row = document.createElement("tr");

        const nameCell = document.createElement("th");
        nameCell.scope = "row";
        nameCell.textContent = employee.name;
        row.appendChild(nameCell);

        currentDays.forEach((day) => {
            const cell = document.createElement("td");
            cell.appendChild(buildCell(employee, day));
            row.appendChild(cell);
        });

        body.appendChild(row);
    });

    const hint = document.getElementById("grid-hint");
    hint.hidden = currentScheduleStatus === "PUBLISHED" && !lastMinuteMode;
    hint.textContent = lastMinuteMode
        ? "Pulsa una celda para cambiar el turno. Se comprueba el convenio igual que en un borrador."
        : "Pulsa una celda para cambiar el turno de esa persona ese día.";
}

/* ---------- edición de una asignación ---------- */

/**
 * Abre el selector de turno de una celda. Antes era un menú flotante posicionado a mano;
 * ahora es el modal del sistema, que se cierra con Escape y no se sale de la pantalla.
 */
function openAssignmentModal(employeeId, date, cell) {
    const employee = currentEmployees.find((e) => e.id === employeeId);
    const currentValue = assignmentsByEmployeeDate.get(employeeId)?.get(date) || null;

    const body = document.createElement("div");
    body.className = "stack-2";

    const intro = document.createElement("p");
    intro.className = "text-secondary";
    intro.style.marginBottom = "var(--s-4)";
    intro.textContent = `${employee ? employee.name : "Esta persona"} · ${formatDayLabel(date)}`;
    body.appendChild(intro);

    currentShiftTemplates.forEach((shiftTemplate) => {
        const option = document.createElement("button");
        option.type = "button";
        option.className = "btn btn--secondary btn--block";
        option.textContent = `${shiftTemplate.name} · ${formatSegments(shiftTemplate.segments)}`;
        if (shiftTemplate.id === currentValue) {
            option.disabled = true;
            option.textContent += " (actual)";
        }
        option.addEventListener("click", () => applyAssignment(employeeId, date, cell, shiftTemplate.id));
        body.appendChild(option);
    });

    if (currentValue) {
        const clear = document.createElement("button");
        clear.type = "button";
        clear.className = "btn btn--secondary btn--block";
        clear.textContent = "Dejar libre";
        clear.addEventListener("click", () => applyAssignment(employeeId, date, cell, null));
        body.appendChild(clear);
    }

    const actions = document.createElement("div");
    actions.className = "row-end";
    actions.style.marginTop = "var(--s-6)";
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "btn btn--quiet";
    cancel.textContent = "Cancelar";
    cancel.addEventListener("click", closeModal);
    actions.appendChild(cancel);
    body.appendChild(actions);

    openModal("Cambiar turno", body);
}

function setAssignmentState(employeeId, date, shiftTemplateId) {
    if (!assignmentsByEmployeeDate.has(employeeId)) {
        assignmentsByEmployeeDate.set(employeeId, new Map());
    }
    const employeeMap = assignmentsByEmployeeDate.get(employeeId);
    if (shiftTemplateId === null) {
        employeeMap.delete(date);
    } else {
        employeeMap.set(date, shiftTemplateId);
    }
}

/**
 * Revalida al vuelo contra PUT /api/schedules/{id}/assignments. Si rompe una dura, el
 * backend la rechaza con 422: la celda se marca en rojo un momento, se explica en el aviso
 * de arriba y no se aplica ningún cambio.
 */
async function applyAssignment(employeeId, date, cell, shiftTemplateId) {
    closeModal();
    try {
        const result = await fetchJson(`/api/schedules/${currentScheduleId}/assignments`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ employeeId, date, shiftTemplateId, lastMinute: lastMinuteMode })
        });
        setAssignmentState(employeeId, date, shiftTemplateId);
        renderGrid();
        const hasWarnings = result.softWarnings && result.softWarnings.length > 0;
        if (hasWarnings) {
            setStatusMessage(result.softWarnings[0], "warn");
        } else if (lastMinuteMode) {
            setStatusMessage("Turno cambiado sobre el cuadrante publicado. Avisa al equipo del cambio.", "ok");
        } else {
            setStatusMessage("Turno cambiado.", "ok");
        }
    } catch (error) {
        if (cell) {
            cell.classList.add("rota-cell--rejected");
            setTimeout(() => cell.classList.remove("rota-cell--rejected"), 2200);
        }
        setStatusMessage(error.message, "alert");
    }
}

/* ---------- cuánta gente hay por franja ---------- */

/**
 * El recuento por franja vive en bands.js, compartido con el panel principal: la misma
 * cuenta contada dos veces acabaría dando dos resultados distintos.
 */
function renderBandSummary() {
    renderBandTable(
        document.getElementById("band-head-row"),
        document.getElementById("band-body"),
        currentDays,
        currentEmployees.map((employee) => employee.id),
        assignmentsByEmployeeDate,
        shiftTemplateById);
}

/* ---------- peticiones escritas ---------- */

const DAY_NAME_TO_INDEX = {
    lunes: 0, martes: 1, miercoles: 2, jueves: 3, viernes: 4, sabado: 5, domingo: 6
};

/** Quita tildes y baja a minúsculas, para poder comparar lo que se escribe a mano. */
function normalize(text) {
    return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
}

/**
 * Entiende una línea del tipo "Ana: tarde jueves".
 *
 * Es un formato con reglas, no lenguaje libre: nombre, dos puntos, y luego un turno de los
 * que haya configurados —o la palabra "libre"— y opcionalmente un día de la semana. Sin día,
 * se aplica a toda la semana. Deliberadamente no hay IA de por medio: el reparto de turnos
 * es determinista y una interpretación equivocada aquí sería un turno mal asignado.
 *
 * @return {{ok:boolean, text:string, employee?:object, shiftTemplateId?:number, dates?:string[]}}
 */
function parseRequestLine(line) {
    const raw = line.trim();
    if (!raw) {
        return null;
    }
    const colon = raw.indexOf(":");
    if (colon < 0) {
        return { ok: false, text: `"${raw}": falta el nombre y los dos puntos.` };
    }

    const namePart = normalize(raw.slice(0, colon));
    const rest = normalize(raw.slice(colon + 1));
    if (!namePart || !rest) {
        return { ok: false, text: `"${raw}": pon el nombre delante y lo que pide detrás.` };
    }

    const matches = currentEmployees.filter((e) => normalize(e.name).includes(namePart));
    if (matches.length === 0) {
        return { ok: false, text: `"${raw}": no hay nadie que se llame así en el local.` };
    }
    if (matches.length > 1) {
        return { ok: false, text: `"${raw}": hay varias personas que encajan (${matches.map((e) => e.name).join(", ")}). Escribe el nombre completo.` };
    }
    const employee = matches[0];

    // El día es opcional: sin él, la petición vale para toda la semana.
    let dates = currentDays.map((d) => d.date);
    let dayLabel = "toda la semana";
    const dayWord = Object.keys(DAY_NAME_TO_INDEX).find((day) => rest.includes(day));
    if (dayWord) {
        const index = DAY_NAME_TO_INDEX[dayWord];
        dates = [currentDays[index].date];
        dayLabel = currentDays[index].label;
    }

    if (rest.includes("libre") || rest.includes("descans")) {
        return { ok: true, employee, shiftTemplateId: null, dates,
            text: `${employee.name}: libre · ${dayLabel}` };
    }

    const shiftTemplate = currentShiftTemplates.find((t) => rest.includes(normalize(t.name)));
    if (!shiftTemplate) {
        const names = currentShiftTemplates.map((t) => t.name).join(", ");
        return { ok: false, text: `"${raw}": no reconozco el turno. Los que hay son: ${names}, o "libre".` };
    }

    return { ok: true, employee, shiftTemplateId: shiftTemplate.id, dates,
        text: `${employee.name}: ${shiftTemplate.name} · ${dayLabel}` };
}

let parsedRequests = [];

/** Enseña qué ha entendido antes de tocar nada. Nada se aplica sin pasar por aquí. */
function checkRequests() {
    const preview = document.getElementById("requests-preview");
    preview.replaceChildren();

    const lines = document.getElementById("requests-input").value.split("\n");
    parsedRequests = lines.map(parseRequestLine).filter((parsed) => parsed !== null);

    parsedRequests.forEach((parsed) => {
        const item = document.createElement("p");
        preview.appendChild(item);
        showNotice(item, parsed.text, parsed.ok ? "ok" : "alert");
    });

    const applicable = parsedRequests.filter((parsed) => parsed.ok);
    document.getElementById("requests-apply").disabled = applicable.length === 0;
    document.getElementById("requests-hint").textContent = parsedRequests.length === 0
        ? "Escribe una petición por línea."
        : `${applicable.length} de ${parsedRequests.length} se pueden aplicar.`;
}

/**
 * Aplica las peticiones entendidas, una a una, por el mismo camino que la edición manual:
 * cada cambio se revalida contra las restricciones duras y se rechaza si rompe alguna.
 */
async function applyRequests() {
    const button = document.getElementById("requests-apply");
    button.disabled = true;

    const applied = [];
    const rejected = [];
    for (const parsed of parsedRequests.filter((p) => p.ok)) {
        for (const date of parsed.dates) {
            try {
                await fetchJson(`/api/schedules/${currentScheduleId}/assignments`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        employeeId: parsed.employee.id,
                        date,
                        shiftTemplateId: parsed.shiftTemplateId,
                        lastMinute: lastMinuteMode
                    })
                });
                setAssignmentState(parsed.employee.id, date, parsed.shiftTemplateId);
                applied.push(parsed.text);
            } catch (error) {
                rejected.push(`${parsed.text}: ${error.message}`);
            }
        }
    }

    renderGrid();
    renderBandSummary();

    if (rejected.length === 0) {
        setStatusMessage(`${applied.length} cambio${applied.length === 1 ? "" : "s"} aplicado${applied.length === 1 ? "" : "s"}.`, "ok");
        document.getElementById("requests-input").value = "";
        document.getElementById("requests-preview").replaceChildren();
        document.getElementById("requests-hint").textContent = "";
        parsedRequests = [];
    } else {
        setStatusMessage(rejected[0], "alert");
        button.disabled = false;
    }
}

/* ---------- huecos de cobertura y equidad ---------- */

function renderCoverage() {
    const section = document.getElementById("coverage-section");
    const list = document.getElementById("coverage-list");
    list.replaceChildren();

    if (!currentUncoveredSlots || currentUncoveredSlots.length === 0) {
        section.hidden = true;
        return;
    }
    section.hidden = false;

    currentUncoveredSlots.forEach((slot) => {
        const shiftTemplate = shiftTemplateById(slot.shiftTemplateId);
        const people = slot.missing === 1 ? "1 persona" : `${slot.missing} personas`;
        const item = document.createElement("p");
        list.appendChild(item);
        showNotice(item,
            `${formatDayLabel(slot.date)}${shiftTemplate ? ", " + shiftTemplate.name : ""}: falta ${people} y no hay nadie que pueda cubrirlo sin saltarse el convenio.`,
            "alert");
    });
}

function renderEquity() {
    const section = document.getElementById("equity-section");
    const list = document.getElementById("equity-list");
    list.replaceChildren();

    if (!currentEquityReport || currentEquityReport.length === 0) {
        section.hidden = true;
        return;
    }
    section.hidden = false;

    [...currentEquityReport]
        .sort((a, b) => b.badShiftsThisWeek - a.badShiftsThisWeek)
        .forEach((entry) => {
            const employee = currentEmployees.find((e) => e.id === entry.employeeId);
            const row = document.createElement("tr");

            const nameCell = document.createElement("td");
            const name = document.createElement("div");
            name.className = "cell-title";
            name.textContent = employee ? employee.name : `Empleado ${entry.employeeId}`;
            nameCell.appendChild(name);
            row.appendChild(nameCell);

            const countCell = document.createElement("td");
            countCell.className = "right";
            countCell.textContent = String(entry.badShiftsThisWeek);
            row.appendChild(countCell);

            list.appendChild(row);
        });
}

/* ---------- estado del tablero (vacío / con datos) ---------- */

/**
 * Un solo botón principal en pantalla: "Generar" mientras no hay cuadrante (dentro del
 * estado vacío) y "Publicar" cuando hay un borrador. Publicado, solo queda imprimir.
 */
function renderActions() {
    const publishButton = document.getElementById("publish-button");
    const printButton = document.getElementById("print-button");
    const lastMinuteButton = document.getElementById("lastminute-button");
    const notifyButton = document.getElementById("notify-button");
    const whatsappButton = document.getElementById("whatsapp-button");
    const badge = document.getElementById("status-badge");
    const mark = document.getElementById("status-mark");

    if (!currentScheduleStatus) {
        publishButton.hidden = true;
        printButton.hidden = true;
        lastMinuteButton.hidden = true;
        notifyButton.hidden = true;
        whatsappButton.hidden = true;
        badge.hidden = true;
        return;
    }

    printButton.hidden = false;
    badge.hidden = false;
    // Solo se comparte un cuadrante publicado: un borrador todavía no es nada para el equipo.
    notifyButton.hidden = currentScheduleStatus !== "PUBLISHED";
    whatsappButton.hidden = currentScheduleStatus !== "PUBLISHED";
    notifyButton.textContent = lastMinuteMode ? "Avisar del cambio" : "Avisar al equipo";
    if (currentScheduleStatus === "PUBLISHED") {
        badge.className = "pill pill--ok";
        mark.className = "mark mark--dot";
        document.getElementById("status-txt").textContent = lastMinuteMode ? "Publicado · editando" : "Publicado";
        publishButton.hidden = true;
        lastMinuteButton.hidden = false;
        lastMinuteButton.textContent = lastMinuteMode ? "Dejar de editar" : "Cambios de última hora";
    } else {
        badge.className = "pill pill--warn";
        mark.className = "mark mark--bars";
        document.getElementById("status-txt").textContent = "Borrador";
        publishButton.hidden = false;
        lastMinuteButton.hidden = true;
    }
}

/**
 * Abre o cierra la edición de un cuadrante ya publicado. Es una acción aparte y no un modo
 * que se herede: el equipo ya ha visto ese cuadrante, así que tocarlo tiene que ser algo que
 * el encargado pide a propósito cada vez.
 */
/**
 * Escribe a quien tiene turno esa semana. Pide confirmación siempre: es lo único de esta
 * pantalla que sale del sistema hacia la plantilla, y una vez enviado no se recoge.
 */
function notifyTeam() {
    const people = new Set();
    assignmentsByEmployeeDate.forEach((byDate, employeeId) => {
        if (byDate.size > 0) {
            people.add(employeeId);
        }
    });

    const question = people.size === 1
        ? "Se escribirá a 1 persona con turno esta semana, contándole solo los suyos."
        : `Se escribirá a ${people.size} personas con turno esta semana, contándole a cada una solo los suyos.`;

    confirmAction(
        lastMinuteMode ? "Avisar del cambio" : "Avisar al equipo",
        `${question} El correo sale ahora y no se puede recoger.`,
        "Enviar aviso",
        async () => {
            const button = document.getElementById("notify-button");
            button.disabled = true;
            try {
                const result = await fetchJson(
                    `/api/schedules/${currentScheduleId}/notify?lastMinute=${lastMinuteMode}`, { method: "POST" });
                setStatusMessage(describeNotifyResult(result), result.mailEnabled ? "ok" : "warn");
            } catch (error) {
                setStatusMessage(error.message, "alert");
            } finally {
                button.disabled = false;
            }
        });
}

/**
 * Redacta el cuadrante de la semana en texto plano y abre WhatsApp con el mensaje ya
 * escrito, para que el encargado lo pegue él en el grupo de un toque.
 *
 * No se publica solo en el grupo, y no por falta de ganas: ninguna API oficial de WhatsApp
 * permite que una aplicación escriba en un grupo normal. Ver la decisión en CLAUDE.md.
 */
function whatsappSummary() {
    const lines = [];
    lines.push(`Cuadrante de la semana ${currentIsoWeek}${currentVenueName ? " · " + currentVenueName : ""}`);
    lines.push(formatDateRangeLabel(currentDays));
    lines.push("");

    currentDays.forEach((day) => {
        const shifts = [];
        currentEmployees.forEach((employee) => {
            const shiftTemplateId = assignmentsByEmployeeDate.get(employee.id)?.get(day.date);
            if (shiftTemplateId) {
                const shiftTemplate = shiftTemplateById(shiftTemplateId);
                shifts.push(`- ${employee.name}: ${shiftTemplate.name} ${formatSegments(shiftTemplate.segments)}`);
            }
        });
        lines.push(day.label);
        lines.push(shifts.length > 0 ? shifts.join("\n") : "- Nadie asignado");
        lines.push("");
    });

    return lines.join("\n").trim();
}

function shareOnWhatsApp() {
    // wa.me abre la app con el texto puesto; enviarlo lo decide y lo hace el encargado.
    window.open(`https://wa.me/?text=${encodeURIComponent(whatsappSummary())}`, "_blank", "noopener");
}

function describeNotifyResult(result) {
    if (!result.mailEnabled) {
        return "No ha salido ningún correo: el envío todavía no está configurado en el servidor. "
            + "El aviso ha quedado anotado en el registro del sistema.";
    }
    const base = result.sent === 1 ? "Avisada 1 persona." : `Avisadas ${result.sent} personas.`;
    if (result.skipped && result.skipped.length > 0) {
        return `${base} Sin avisar, por no tener correo o por haber fallado el envío: ${result.skipped.join(", ")}.`;
    }
    return base;
}

function toggleLastMinuteMode() {
    lastMinuteMode = !lastMinuteMode;
    renderActions();
    renderGrid();
    setStatusMessage(
        lastMinuteMode
            ? "Estás editando un cuadrante publicado. Los cambios se guardan al momento y el equipo no se entera solo."
            : null,
        "warn");
}

function showBoard() {
    document.getElementById("board-body").hidden = false;
    document.getElementById("empty-state").hidden = true;
    document.getElementById("empty-week-picker").hidden = true;
    renderActions();
}

function showEmptyState() {
    currentScheduleId = null;
    currentScheduleStatus = null;
    document.getElementById("board-body").hidden = true;
    document.getElementById("empty-state").hidden = false;
    // Sin cuadrante no hay nada arriba, así que los mandos de semana bajan al estado vacío.
    document.getElementById("empty-week-picker").hidden = false;
    document.getElementById("coverage-section").hidden = true;
    renderActions();
}

async function applyState(data) {
    // Cargar una semana cierra siempre el modo de última hora: no se hereda de la anterior.
    lastMinuteMode = false;
    currentVenueId = data.venueId;
    currentIsoYear = data.isoYear;
    currentIsoWeek = data.isoWeek;
    currentScheduleId = data.scheduleId;
    currentScheduleStatus = data.status;
    currentEmployees = data.employees;
    currentShiftTemplates = data.shiftTemplates;
    currentUncoveredSlots = data.uncoveredSlots;
    currentEquityReport = data.equityReport;
    currentDays = buildWeekDays(data.isoYear, data.isoWeek);
    assignmentsByEmployeeDate = groupAssignments(data.assignments);

    const weekDateSet = new Set(currentDays.map((d) => d.date));
    unavailableSet = await loadUnavailableSet(data.employees.map((e) => e.id), weekDateSet);

    renderWeekLabel();
    renderHead();
    renderGrid();
    renderBandSummary();
    renderCoverage();
    renderEquity();
    showBoard();

    fetchJson(`/api/venues/${data.venueId}`)
        .then((venue) => {
            currentVenueName = venue.name;
            if (window.updateShellVenueName) window.updateShellVenueName(venue.name);
            document.getElementById("print-title").textContent = venue.name;
        })
        .catch(() => {});
}

/* ---------- acciones principales ---------- */

async function loadExistingWeek() {
    const venueId = getVenueId();
    const isoYear = getIsoYear();
    const isoWeek = getIsoWeek();
    if (!venueId || !isoYear || !isoWeek) {
        return;
    }

    try {
        const { employees, shiftTemplates } = await loadReferenceData(venueId);
        const schedule = await fetchJson(`/api/schedules?venueId=${venueId}&isoYear=${isoYear}&isoWeek=${isoWeek}`);
        await applyState({
            venueId, isoYear, isoWeek,
            scheduleId: schedule.scheduleId,
            status: schedule.status,
            employees, shiftTemplates,
            assignments: schedule.assignments,
            uncoveredSlots: null,
            equityReport: null
        });
        setStatusMessage(null);
    } catch (error) {
        showEmptyState();
        if (error.message && error.message.startsWith("Venue no encontrado")) {
            setStatusMessage(error.message, "alert");
        } else {
            setStatusMessage(null);
        }
    }
}

let generating = false;

async function generateWeek() {
    if (generating) {
        return;
    }
    const venueId = getVenueId();
    const isoYear = getIsoYear();
    const isoWeek = getIsoWeek();
    if (!venueId || !isoYear || !isoWeek) {
        setStatusMessage("Indica la semana y el año.", "warn");
        return;
    }

    generating = true;
    const button = document.getElementById("empty-generate-button");
    button.disabled = true;

    try {
        const { employees, shiftTemplates } = await loadReferenceData(venueId);
        const response = await fetchJson("/api/schedules/generate", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ venueId, isoYear, isoWeek })
        });
        await applyState({
            venueId, isoYear, isoWeek,
            scheduleId: response.scheduleId,
            status: response.status,
            employees, shiftTemplates,
            assignments: response.assignments,
            uncoveredSlots: response.uncoveredSlots,
            equityReport: response.equityReport
        });
        setStatusMessage("Propuesta generada. Revísala antes de publicarla.", "ok");
    } catch (error) {
        setStatusMessage(error.message, "alert");
    } finally {
        button.disabled = false;
        generating = false;
    }
}

async function publishSchedule() {
    if (!currentScheduleId) {
        return;
    }
    const button = document.getElementById("publish-button");
    button.disabled = true;
    try {
        const result = await fetchJson(`/api/schedules/${currentScheduleId}/publish`, { method: "POST" });
        currentScheduleStatus = result.status;
        renderActions();
        renderGrid();
        const hasWarnings = result.softWarnings && result.softWarnings.length > 0;
        // Publicar no escribe a nadie: avisar es un botón aparte que pulsa el encargado.
        setStatusMessage(
            hasWarnings
                ? `Cuadrante publicado, con un aviso: ${result.softWarnings[0]}. Usa "Avisar al equipo" cuando quieras enviarlo.`
                : "Cuadrante publicado. Usa \"Avisar al equipo\" para escribirles.",
            hasWarnings ? "warn" : "ok");
    } catch (error) {
        setStatusMessage(error.message, "alert");
    } finally {
        button.disabled = false;
    }
}

function shiftWeek(delta) {
    const monday = mondayOfIsoWeek(getIsoYear(), getIsoWeek());
    monday.setUTCDate(monday.getUTCDate() + delta * 7);
    const { isoYear, isoWeek } = isoYearWeekOfDate(monday);
    setWeekInputs(isoYear, isoWeek);
    loadExistingWeek();
}

/* ---------- init ---------- */

/** Los dos formularios de semana (el de arriba y el del estado vacío) van sincronizados. */
function setWeekInputs(isoYear, isoWeek) {
    document.getElementById("iso-year-input").value = isoYear;
    document.getElementById("iso-week-input").value = isoWeek;
    document.getElementById("empty-year-input").value = isoYear;
    document.getElementById("empty-week-input").value = isoWeek;
}

document.addEventListener("DOMContentLoaded", () => {
    const { isoYear, isoWeek } = currentIsoYearWeek();
    setWeekInputs(isoYear, isoWeek);

    document.getElementById("requests-check").addEventListener("click", checkRequests);
    document.getElementById("requests-apply").addEventListener("click", applyRequests);

    document.getElementById("empty-week-form").addEventListener("submit", (event) => {
        event.preventDefault();
        setWeekInputs(
            Number(document.getElementById("empty-year-input").value),
            Number(document.getElementById("empty-week-input").value));
        loadExistingWeek();
    });

    document.getElementById("wk-prev").addEventListener("click", () => shiftWeek(-1));
    document.getElementById("wk-next").addEventListener("click", () => shiftWeek(1));
    document.getElementById("empty-generate-button").addEventListener("click", generateWeek);
    document.getElementById("publish-button").addEventListener("click", publishSchedule);
    document.getElementById("lastminute-button").addEventListener("click", toggleLastMinuteMode);
    document.getElementById("notify-button").addEventListener("click", notifyTeam);
    document.getElementById("whatsapp-button").addEventListener("click", shareOnWhatsApp);
    document.getElementById("print-button").addEventListener("click", () => {
        closeModal();
        window.print();
    });

    document.getElementById("week-form").addEventListener("submit", (event) => {
        event.preventDefault();
        loadExistingWeek();
    });

    document.getElementById("schedule-body").addEventListener("click", (event) => {
        const cell = event.target.closest("button.rota-cell");
        if (cell && !cell.disabled) {
            openAssignmentModal(Number(cell.dataset.employeeId), cell.dataset.date, cell);
        }
    });

    fetchJson("/api/auth/me").then((me) => {
        if (me.role !== "MANAGER") {
            window.location.href = "employee.html";
            return;
        }
        currentVenueId = me.venueId;
        loadExistingWeek();
    }).catch((error) => setStatusMessage(error.message, "alert"));
});
