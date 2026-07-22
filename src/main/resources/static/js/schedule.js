const DAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
const WEEKEND_INDEXES = [4, 5, 6];
const MONTH_LABELS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
/** Paleta categórica para diferenciar personas en los avatares (no es semántica de turno, no va en tokens.css). */
const AVATAR_COLORS = ["#0F5257", "#C0673F", "#3B6EA5", "#8B5A8C", "#2F8A5B", "#B4842B", "#4A5568", "#A03A4E", "#2C7A7B", "#6B4E9E"];

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
    return response.json();
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

function stripAccents(text) {
    return text.normalize("NFD").replace(/[̀-ͯ]/g, "");
}

/**
 * ShiftTemplate es configurable por venue, así que no hay garantía de que un
 * turno se llame "Mañana"/"Tarde"/"Partido". Los reconocemos por nombre para
 * darles su color de firma; cualquier otro nombre usa el color neutro "n".
 */
function shiftColorClass(shiftTemplate) {
    const name = stripAccents(shiftTemplate.name).toUpperCase();
    if (name.includes("MANANA") || name.includes("MORNING")) return "m";
    if (name.includes("TARDE") || name.includes("AFTERNOON")) return "t";
    if (name.includes("PARTID") || name.includes("SPLIT")) return "p";
    return "n";
}

function initials(name) {
    return name.split(" ").map((w) => w[0]).slice(0, 2).join("").toUpperCase();
}

function avatarColor(index) {
    return AVATAR_COLORS[index % AVATAR_COLORS.length];
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

function getVenueId() {
    return Number(document.getElementById("venue-id-input").value);
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

function renderLegend() {
    const legend = document.getElementById("legend");
    legend.innerHTML = "";

    currentShiftTemplates.forEach((shiftTemplate) => {
        const item = document.createElement("span");
        item.className = "lg";
        const swatch = document.createElement("span");
        swatch.className = `sw sw-${shiftColorClass(shiftTemplate)}`;
        item.appendChild(swatch);
        item.appendChild(document.createTextNode(` ${shiftTemplate.name} · ${formatSegmentsShort(shiftTemplate.segments)}`));
        legend.appendChild(item);
    });

    const libre = document.createElement("span");
    libre.className = "lg";
    const swatch = document.createElement("span");
    swatch.className = "sw sw-l";
    libre.appendChild(swatch);
    libre.appendChild(document.createTextNode(" Libre"));
    legend.appendChild(libre);
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

function buildChip(shiftTemplate, isUnavailable) {
    const chip = document.createElement("div");
    if (isUnavailable) {
        chip.className = "chip na";
        chip.textContent = "No disp.";
        return chip;
    }
    if (!shiftTemplate) {
        chip.className = "chip l";
        chip.textContent = "Libre";
        return chip;
    }
    chip.className = `chip ${shiftColorClass(shiftTemplate)}`;
    const label = document.createElement("span");
    label.textContent = shiftTemplate.name;
    chip.appendChild(label);
    const time = document.createElement("span");
    time.className = "tm tnum";
    time.textContent = formatSegmentsShort(shiftTemplate.segments);
    chip.appendChild(time);
    return chip;
}

function renderGrid() {
    const body = document.getElementById("schedule-body");
    body.innerHTML = "";

    currentEmployees.forEach((employee, index) => {
        const row = document.createElement("tr");

        const nameCell = document.createElement("td");
        nameCell.className = "name-col";
        const empWrap = document.createElement("div");
        empWrap.className = "emp";
        const avatar = document.createElement("span");
        avatar.className = "av";
        avatar.style.background = avatarColor(index);
        avatar.textContent = initials(employee.name);
        const nameSpan = document.createElement("span");
        nameSpan.className = "nm";
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

            cell.appendChild(buildChip(shiftTemplate, isUnavailable));
            row.appendChild(cell);
        });

        body.appendChild(row);
    });

    document.getElementById("table-wrap").classList.toggle("locked", currentScheduleStatus === "PUBLISHED");
}

function triggerFillingAnimation() {
    const wrap = document.getElementById("table-wrap");
    wrap.querySelectorAll(".chip").forEach((chip, index) => {
        chip.style.animationDelay = `${index * 11}ms`;
    });
    wrap.classList.add("filling");
    setTimeout(() => wrap.classList.remove("filling"), 700);
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
        button.className = "pop-opt";

        const sq = document.createElement("span");
        sq.className = `sq ${shiftColorClass(shiftTemplate)}`;
        button.appendChild(sq);

        const label = document.createElement("span");
        label.textContent = shiftTemplate.name;
        button.appendChild(label);

        const small = document.createElement("small");
        small.className = "tnum";
        small.textContent = formatSegmentsShort(shiftTemplate.segments);
        button.appendChild(small);

        button.addEventListener("click", () => applyAssignment(shiftTemplate.id));
        container.appendChild(button);
    });

    const currentValue = assignmentsByEmployeeDate.get(popTarget.employeeId)?.get(popTarget.date);
    if (currentValue) {
        const removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.className = "pop-opt";

        const sq = document.createElement("span");
        sq.className = "sq x";
        removeButton.appendChild(sq);

        const label = document.createElement("span");
        label.textContent = "Quitar";
        removeButton.appendChild(label);

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
        renderPanels();
        const hasWarnings = result.softWarnings && result.softWarnings.length > 0;
        showToast(hasWarnings ? result.softWarnings[0] : "Turno actualizado", hasWarnings ? "warn" : undefined);
    } catch (error) {
        flagViolation(cell);
        showToast(error.message, "warn");
    }
}

/* ---------- paneles laterales ---------- */

function renderPanels() {
    renderCoveragePanel();
    renderEquityPanel();
}

function renderCoveragePanel() {
    const list = document.getElementById("coverage-list");
    const pill = document.getElementById("coverage-pill");
    list.innerHTML = "";

    if (currentUncoveredSlots === null) {
        pill.hidden = true;
        appendPanelHint(list, "Los huecos de cobertura se calculan al generar; edítalos y vuelve a generar para verlos al día.");
        return;
    }
    if (currentUncoveredSlots.length === 0) {
        pill.hidden = true;
        appendPanelHint(list, "Cobertura completa.");
        return;
    }

    pill.hidden = false;
    pill.textContent = `${currentUncoveredSlots.length} hueco${currentUncoveredSlots.length === 1 ? "" : "s"}`;

    currentUncoveredSlots.forEach((slot) => {
        const shiftTemplate = currentShiftTemplates.find((t) => t.id === slot.shiftTemplateId);
        const row = document.createElement("div");
        row.className = "gap-row";

        const badge = document.createElement("span");
        badge.className = "gd";
        badge.textContent = String(slot.missing);
        row.appendChild(badge);

        const text = document.createElement("span");
        text.appendChild(document.createTextNode("Falta cubrir "));
        const strong = document.createElement("b");
        strong.textContent = `${slot.date}${shiftTemplate ? " · " + shiftTemplate.name : ""}`;
        text.appendChild(strong);
        row.appendChild(text);

        const small = document.createElement("small");
        small.textContent = "sin candidato";
        row.appendChild(small);

        list.appendChild(row);
    });
}

function renderEquityPanel() {
    const list = document.getElementById("equity-list");
    list.innerHTML = "";

    if (currentEquityReport === null) {
        appendPanelHint(list, "La equidad se calcula al generar; edítalos y vuelve a generar para verla al día.");
        return;
    }
    if (currentEquityReport.length === 0) {
        appendPanelHint(list, "Sin datos de equidad para esta semana.");
        return;
    }

    const sorted = [...currentEquityReport].sort((a, b) => b.badShiftsThisWeek - a.badShiftsThisWeek);
    const max = Math.max(...sorted.map((e) => e.badShiftsThisWeek), 1);

    sorted.forEach((entry) => {
        const employee = currentEmployees.find((e) => e.id === entry.employeeId);
        const row = document.createElement("div");
        row.className = "eq-row";

        const name = document.createElement("span");
        name.textContent = employee ? employee.name.split(" ")[0] : `#${entry.employeeId}`;
        row.appendChild(name);

        const bar = document.createElement("span");
        bar.className = "eq-bar";
        const fill = document.createElement("span");
        fill.style.width = `${(entry.badShiftsThisWeek / max) * 100}%`;
        bar.appendChild(fill);
        row.appendChild(bar);

        const val = document.createElement("span");
        val.className = "val tnum";
        val.textContent = String(entry.badShiftsThisWeek);
        row.appendChild(val);

        list.appendChild(row);
    });
}

function appendPanelHint(list, message) {
    const hint = document.createElement("p");
    hint.className = "panel-empty";
    hint.textContent = message;
    list.appendChild(hint);
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
    badge.className = "badge " + (currentScheduleStatus === "PUBLISHED" ? "badge-pub" : "badge-draft");
    document.getElementById("status-txt").textContent = currentScheduleStatus === "PUBLISHED" ? "Publicado" : "Borrador";
    publishButton.hidden = currentScheduleStatus === "PUBLISHED";
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
    renderLegend();
    renderHead();
    renderGrid();
    renderPanels();
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
        triggerFillingAnimation();
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

    document.getElementById("venue-id-input").addEventListener("change", loadExistingWeek);
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

    loadExistingWeek();
});
