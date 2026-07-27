const DAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
const WEEKEND_INDEXES = [4, 5, 6];
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
let popTarget = null;

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

/* ---------- formato de turnos ---------- */

function formatTime(localTime) {
    return localTime.slice(0, 5);
}

/** "08:00-16:00" -> "08–16"; varios tramos (partido) -> "12·20" (hora de inicio de cada tramo). */
function formatSegmentsShort(segments) {
    if (!segments || segments.length === 0) return "";
    if (segments.length === 1) {
        return `${formatTime(segments[0].startTime)}–${formatTime(segments[0].endTime)}`;
    }
    return segments.map((s) => formatTime(s.startTime).slice(0, 2)).join("·");
}

function initials(name) {
    return name.split(" ").map((w) => w[0]).slice(0, 2).join("").toUpperCase();
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

/* ---------- inputs del topbar ---------- */

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

/* ---------- render: cabecera, leyenda, rejilla ---------- */

function renderWeekLabel() {
    const rangeLabel = formatDateRangeLabel(currentDays);
    document.getElementById("wk-sub").textContent = rangeLabel;
    document.getElementById("print-subtitle").textContent =
        `Semana ${currentIsoWeek}/${currentIsoYear} · ${rangeLabel}`;
}

function renderHead() {
    const headRow = document.getElementById("schedule-head-row");
    headRow.innerHTML = "";

    const nameTh = document.createElement("th");
    nameTh.className = "name-col";
    nameTh.textContent = "Persona";
    headRow.appendChild(nameTh);

    currentDays.forEach((day, index) => {
        const th = document.createElement("th");
        if (WEEKEND_INDEXES.includes(index)) th.className = "wknd";
        th.textContent = day.label;
        headRow.appendChild(th);
    });
}

function buildShiftCell(shiftTemplate, isUnavailable) {
    const wrap = document.createElement("div");
    if (isUnavailable) {
        wrap.className = "shift-chip shift-chip--unavailable";
        wrap.textContent = "No disp.";
        return wrap;
    }
    if (!shiftTemplate) {
        wrap.className = "shift-chip shift-chip--empty";
        wrap.textContent = "Libre";
        return wrap;
    }
    wrap.className = "shift-chip";
    const label = document.createElement("span");
    label.textContent = shiftTemplate.name;
    wrap.appendChild(label);
    const time = document.createElement("span");
    time.className = "chip-time";
    time.textContent = formatSegmentsShort(shiftTemplate.segments);
    wrap.appendChild(time);
    return wrap;
}

function renderGrid() {
    const body = document.getElementById("schedule-body");
    body.innerHTML = "";

    currentEmployees.forEach((employee) => {
        const row = document.createElement("tr");

        const nameCell = document.createElement("td");
        nameCell.className = "name-col";
        const empWrap = document.createElement("div");
        empWrap.className = "emp-cell";
        const avatar = document.createElement("span");
        avatar.className = "avatar";
        avatar.textContent = initials(employee.name);
        const nameSpan = document.createElement("span");
        nameSpan.textContent = employee.name;
        empWrap.appendChild(avatar);
        empWrap.appendChild(nameSpan);
        nameCell.appendChild(empWrap);
        row.appendChild(nameCell);

        currentDays.forEach((day) => {
            const cell = document.createElement("td");
            cell.className = "cell";
            cell.dataset.employeeId = String(employee.id);
            cell.dataset.date = day.date;

            const shiftTemplateId = assignmentsByEmployeeDate.get(employee.id)?.get(day.date) || null;
            const shiftTemplate = shiftTemplateId ? currentShiftTemplates.find((t) => t.id === shiftTemplateId) : null;
            const isUnavailable = unavailableSet.has(`${employee.id},${day.date}`);

            cell.appendChild(buildShiftCell(shiftTemplate, isUnavailable));
            row.appendChild(cell);
        });

        body.appendChild(row);
    });

    document.getElementById("schedule-table").classList.toggle("locked", currentScheduleStatus === "PUBLISHED");
}

/* ---------- edición: popover ---------- */

function openPopover(cell, employeeId, date) {
    if (currentScheduleStatus === "PUBLISHED") return;
    const key = `${employeeId},${date}`;
    if (unavailableSet.has(key)) {
        const employee = currentEmployees.find((e) => e.id === employeeId);
        showToast(`${employee ? employee.name.split(" ")[0] : "Esta persona"} no está disponible ese día`, "warn");
        return;
    }
    popTarget = { employeeId, date, cell };
    renderPopoverOptions();
    positionPopover(cell);
}

function renderPopoverOptions() {
    const container = document.getElementById("pop-options");
    container.innerHTML = "";

    currentShiftTemplates.forEach((shiftTemplate) => {
        const button = document.createElement("button");
        button.type = "button";

        const label = document.createElement("span");
        label.textContent = shiftTemplate.name;
        button.appendChild(label);

        const small = document.createElement("small");
        small.textContent = formatSegmentsShort(shiftTemplate.segments);
        button.appendChild(small);

        button.addEventListener("click", () => applyAssignment(shiftTemplate.id));
        container.appendChild(button);
    });

    const currentValue = assignmentsByEmployeeDate.get(popTarget.employeeId)?.get(popTarget.date);
    if (currentValue) {
        const removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.className = "remove";
        removeButton.textContent = "Quitar turno";
        removeButton.addEventListener("click", () => applyAssignment(null));
        container.appendChild(removeButton);
    }
}

function positionPopover(cell) {
    const pop = document.getElementById("pop");
    pop.classList.add("show");
    pop.style.left = "0px";
    pop.style.top = "0px";
    const rect = cell.getBoundingClientRect();
    const popWidth = pop.offsetWidth;
    const popHeight = pop.offsetHeight;
    let x = rect.left;
    let y = rect.bottom + 6;
    if (x + popWidth > window.innerWidth - 10) x = window.innerWidth - popWidth - 10;
    if (y + popHeight > window.innerHeight - 10) y = rect.top - popHeight - 6;
    pop.style.left = `${Math.max(10, x)}px`;
    pop.style.top = `${Math.max(10, y)}px`;
}

function closePopover() {
    document.getElementById("pop").classList.remove("show");
    popTarget = null;
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

function flagViolation(cell) {
    cell.classList.add("violation");
    setTimeout(() => cell.classList.remove("violation"), 2200);
}

/**
 * Revalida al vuelo contra PUT /api/schedules/{id}/assignments. Si rompe una
 * dura, el backend la rechaza (422): la celda se marca en rojo con el shake
 * y no se aplica ningún cambio local.
 */
async function applyAssignment(shiftTemplateId) {
    const { employeeId, date, cell } = popTarget;
    closePopover();
    try {
        const result = await fetchJson(`/api/schedules/${currentScheduleId}/assignments`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ employeeId, date, shiftTemplateId })
        });
        setAssignmentState(employeeId, date, shiftTemplateId);
        renderGrid();
        const hasWarnings = result.softWarnings && result.softWarnings.length > 0;
        showToast(hasWarnings ? result.softWarnings[0] : "Turno actualizado", hasWarnings ? "warn" : undefined);
    } catch (error) {
        flagViolation(cell);
        showToast(error.message, "warn");
    }
}

/* ---------- estado del tablero (vacío / con datos) ---------- */

function renderTopbarActions() {
    const generateButton = document.getElementById("generate-button");
    const publishButton = document.getElementById("publish-button");
    const printButton = document.getElementById("print-button");
    const badge = document.getElementById("status-badge");

    if (!currentScheduleStatus) {
        generateButton.hidden = false;
        publishButton.hidden = true;
        printButton.hidden = true;
        badge.hidden = true;
        return;
    }

    // Ya existe un cuadrante para esta semana: no hay endpoint para regenerarlo.
    generateButton.hidden = true;
    printButton.hidden = false;
    badge.hidden = false;
    badge.className = "pill " + (currentScheduleStatus === "PUBLISHED" ? "pill--ok" : "pill--warn");
    document.getElementById("status-txt").textContent = currentScheduleStatus === "PUBLISHED" ? "Publicado" : "Borrador";
    publishButton.hidden = currentScheduleStatus === "PUBLISHED";
}

/* ---------- huecos de cobertura y equidad ---------- */

function renderCoverageList() {
    const list = document.getElementById("coverage-list");
    list.innerHTML = "";

    if (currentUncoveredSlots === null) {
        return;
    }
    if (currentUncoveredSlots.length === 0) {
        const notice = document.createElement("div");
        notice.className = "notice notice--ok";
        notice.setAttribute("role", "status");
        notice.innerHTML = '<span class="mark mark--dot"></span>Cobertura completa';
        list.appendChild(notice);
        return;
    }

    currentUncoveredSlots.forEach((slot) => {
        const shiftTemplate = currentShiftTemplates.find((t) => t.id === slot.shiftTemplateId);
        const notice = document.createElement("div");
        notice.className = "notice notice--alert";
        notice.setAttribute("role", "status");

        const mark = document.createElement("span");
        mark.className = "mark mark--dot";
        notice.appendChild(mark);

        const text = document.createElement("span");
        text.textContent = `Falta${slot.missing === 1 ? "" : "n"} ${slot.missing} persona${slot.missing === 1 ? "" : "s"} · ${slot.date}${shiftTemplate ? " · " + shiftTemplate.name : ""}`;
        notice.appendChild(text);

        list.appendChild(notice);
    });
}

function renderEquityTable() {
    const section = document.getElementById("equity-section");
    const body = document.getElementById("equity-table-body");
    body.innerHTML = "";

    if (currentEquityReport === null || currentEquityReport.length === 0) {
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
            nameCell.textContent = employee ? employee.name : `#${entry.employeeId}`;
            row.appendChild(nameCell);

            const valueCell = document.createElement("td");
            valueCell.className = "right";
            valueCell.textContent = String(entry.badShiftsThisWeek);
            row.appendChild(valueCell);

            body.appendChild(row);
        });
}

function showBoard() {
    document.getElementById("board-body").style.display = "";
    document.getElementById("empty-state").style.display = "none";
    document.getElementById("board-sub").textContent =
        `${currentEmployees.length} personas · ${currentShiftTemplates.length} turnos · pulsa una celda para editarla`;
    renderTopbarActions();
}

function showEmptyState() {
    currentScheduleId = null;
    currentScheduleStatus = null;
    document.getElementById("board-body").style.display = "none";
    document.getElementById("empty-state").style.display = "";
    renderTopbarActions();
}

async function applyState(data) {
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
    renderCoverageList();
    renderEquityTable();
    showBoard();

    fetchJson(`/api/venues/${data.venueId}`)
        .then((venue) => {
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
    if (!venueId || !isoYear || !isoWeek) return;

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
    } catch (error) {
        showEmptyState();
        if (error.message && error.message.startsWith("Venue no encontrado")) {
            showToast(error.message, "warn");
        }
    }
}

let generating = false;

async function generateWeek() {
    if (generating) return;
    const venueId = getVenueId();
    const isoYear = getIsoYear();
    const isoWeek = getIsoWeek();
    if (!venueId || !isoYear || !isoWeek) {
        showToast("Indica venue, año y semana", "warn");
        return;
    }

    generating = true;
    const buttons = [document.getElementById("generate-button"), document.getElementById("empty-generate-button")];
    buttons.forEach((b) => { b.disabled = true; });

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
        showToast("Propuesta generada · revísala antes de publicar");
    } catch (error) {
        showToast(error.message, "warn");
    } finally {
        buttons.forEach((b) => { b.disabled = false; });
        generating = false;
    }
}

async function publishSchedule() {
    if (!currentScheduleId) return;
    const button = document.getElementById("publish-button");
    button.disabled = true;
    try {
        const result = await fetchJson(`/api/schedules/${currentScheduleId}/publish`, { method: "POST" });
        currentScheduleStatus = result.status;
        renderTopbarActions();
        renderGrid();
        const hasWarnings = result.softWarnings && result.softWarnings.length > 0;
        showToast(
            hasWarnings ? `Publicado con avisos: ${result.softWarnings[0]}` : "Cuadrante publicado · el equipo ha sido avisado",
            hasWarnings ? "warn" : undefined
        );
    } catch (error) {
        showToast(error.message, "warn");
    } finally {
        button.disabled = false;
    }
}

function shiftWeek(delta) {
    const monday = mondayOfIsoWeek(getIsoYear(), getIsoWeek());
    monday.setUTCDate(monday.getUTCDate() + delta * 7);
    const { isoYear, isoWeek } = isoYearWeekOfDate(monday);
    document.getElementById("iso-year-input").value = isoYear;
    document.getElementById("iso-week-input").value = isoWeek;
    loadExistingWeek();
}

/* ---------- init ---------- */

document.addEventListener("DOMContentLoaded", () => {
    const { isoYear, isoWeek } = currentIsoYearWeek();
    document.getElementById("iso-year-input").value = isoYear;
    document.getElementById("iso-week-input").value = isoWeek;

    document.getElementById("iso-year-input").addEventListener("change", loadExistingWeek);
    document.getElementById("iso-week-input").addEventListener("change", loadExistingWeek);
    document.getElementById("wk-prev").addEventListener("click", () => shiftWeek(-1));
    document.getElementById("wk-next").addEventListener("click", () => shiftWeek(1));
    document.getElementById("generate-button").addEventListener("click", generateWeek);
    document.getElementById("empty-generate-button").addEventListener("click", generateWeek);
    document.getElementById("publish-button").addEventListener("click", publishSchedule);
    document.getElementById("print-button").addEventListener("click", () => {
        closePopover();
        window.print();
    });

    document.getElementById("schedule-body").addEventListener("click", (event) => {
        const cell = event.target.closest("td.cell");
        if (cell) {
            openPopover(cell, Number(cell.dataset.employeeId), cell.dataset.date);
        }
    });

    document.addEventListener("click", (event) => {
        const pop = document.getElementById("pop");
        if (pop.classList.contains("show") && !pop.contains(event.target) && !event.target.closest("td.cell")) {
            closePopover();
        }
    });
    window.addEventListener("scroll", closePopover, true);

    fetchJson("/api/auth/me").then((me) => {
        if (me.role !== "MANAGER") {
            window.location.href = "employee.html";
            return;
        }
        currentVenueId = me.venueId;
        loadExistingWeek();
    }).catch((error) => showToast(error.message, "warn"));
});
