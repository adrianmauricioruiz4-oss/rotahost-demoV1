const DAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

/** Metadatos del último cuadrante generado, para el botón de publicar y la cabecera. */
let currentWeekMeta = null;

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

function currentIsoYearWeek() {
    const now = new Date();
    const target = new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()));
    const dayNumber = (target.getUTCDay() + 6) % 7;
    target.setUTCDate(target.getUTCDate() - dayNumber + 3);
    const firstThursday = new Date(Date.UTC(target.getUTCFullYear(), 0, 4));
    const firstThursdayDayNumber = (firstThursday.getUTCDay() + 6) % 7;
    firstThursday.setUTCDate(firstThursday.getUTCDate() - firstThursdayDayNumber + 3);
    const isoWeek = 1 + Math.round((target - firstThursday) / (7 * 24 * 60 * 60 * 1000));
    return { isoYear: target.getUTCFullYear(), isoWeek };
}

function renderSchedule(root, week) {
    const headRow = root.getElementById("schedule-head-row");
    const body = root.getElementById("schedule-body");

    headRow.querySelectorAll(".day-col").forEach((el) => el.remove());
    week.days.forEach((day) => {
        const th = document.createElement("th");
        th.className = "day-col";
        th.textContent = day.label;
        headRow.appendChild(th);
    });

    body.innerHTML = "";
    const assignmentsByEmployee = groupAssignmentsByEmployee(week.assignments);

    week.employees.forEach((employee) => {
        const row = document.createElement("tr");

        const nameCell = document.createElement("td");
        nameCell.className = "employee-col";
        nameCell.textContent = employee.name;
        row.appendChild(nameCell);

        const employeeAssignments = assignmentsByEmployee.get(employee.id) || new Map();
        week.days.forEach((day) => {
            const cell = document.createElement("td");
            const currentShiftTemplateId = employeeAssignments.get(day.date) || null;
            cell.appendChild(buildAssignmentSelect(root, week, employee.id, day.date, currentShiftTemplateId));
            row.appendChild(cell);
        });

        body.appendChild(row);
    });
}

function groupAssignmentsByEmployee(assignments) {
    const map = new Map();
    assignments.forEach((assignment) => {
        if (!map.has(assignment.employeeId)) {
            map.set(assignment.employeeId, new Map());
        }
        map.get(assignment.employeeId).set(assignment.date, assignment.shiftTemplateId);
    });
    return map;
}

/** Cada celda es un <select>: elegir un turno lo asigna, "—" lo quita. */
function buildAssignmentSelect(root, week, employeeId, date, currentShiftTemplateId) {
    const select = document.createElement("select");
    select.className = "shift-select";

    const emptyOption = document.createElement("option");
    emptyOption.value = "";
    emptyOption.textContent = "—";
    select.appendChild(emptyOption);

    week.shiftTemplates.forEach((shiftTemplate) => {
        const option = document.createElement("option");
        option.value = String(shiftTemplate.id);
        option.textContent = shiftTemplate.label;
        select.appendChild(option);
    });

    select.value = currentShiftTemplateId ? String(currentShiftTemplateId) : "";
    select.dataset.previousValue = select.value;

    select.addEventListener("change", () => handleAssignmentChange(root, week, employeeId, date, select));
    return select;
}

/**
 * Revalida al vuelo contra PUT /api/schedules/{id}/assignments. Si la edición
 * rompe una restricción dura, el backend la rechaza (422): se revierte el
 * <select> a su valor anterior y se marca en rojo.
 */
async function handleAssignmentChange(root, week, employeeId, date, select) {
    select.classList.remove("shift-select-error");
    const previousValue = select.dataset.previousValue;
    const newShiftTemplateId = select.value ? Number(select.value) : null;
    select.disabled = true;

    try {
        const result = await fetchJson(`/api/schedules/${week.scheduleId}/assignments`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ employeeId, date, shiftTemplateId: newShiftTemplateId })
        });
        select.dataset.previousValue = select.value;
        const hasWarnings = result.softWarnings && result.softWarnings.length > 0;
        const warningText = hasWarnings ? ` Aviso: ${result.softWarnings.join("; ")}` : "";
        setStatusMessage(root, `Turno actualizado.${warningText}`, hasWarnings);
    } catch (error) {
        select.value = previousValue;
        select.classList.add("shift-select-error");
        setStatusMessage(root, error.message, true);
    } finally {
        select.disabled = false;
    }
}

function renderUncoveredSlots(root, uncoveredSlots, shiftTemplateLabelsById) {
    const list = root.getElementById("uncovered-list");
    list.innerHTML = "";
    if (!uncoveredSlots || uncoveredSlots.length === 0) {
        list.hidden = true;
        return;
    }
    uncoveredSlots.forEach((slot) => {
        const shiftLabel = shiftTemplateLabelsById.get(slot.shiftTemplateId) || `turno #${slot.shiftTemplateId}`;
        const item = document.createElement("li");
        item.textContent = `${slot.date} · ${shiftLabel} · faltan ${slot.missing}`;
        list.appendChild(item);
    });
    list.hidden = false;
}

function setStatusMessage(root, message, isError) {
    const el = root.getElementById("status-message");
    if (!message) {
        el.hidden = true;
        return;
    }
    el.textContent = message;
    el.className = isError ? "status-message error" : "status-message success";
    el.hidden = false;
}

function updateWeekLabel(root, status) {
    root.getElementById("week-label").textContent =
        `Semana ISO ${currentWeekMeta.isoWeek} · ${currentWeekMeta.isoYear} — cuadrante #${currentWeekMeta.scheduleId} (${status})`;
}

/** Al publicar, el backend ya rechaza más ediciones (409); aquí solo lo reflejamos en la UI. */
function lockScheduleForEditing(root) {
    root.querySelectorAll(".shift-select").forEach((select) => {
        select.disabled = true;
    });
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

async function loadReferenceData(venueId) {
    const [employees, allShiftTemplates] = await Promise.all([
        fetchJson("/api/employees"),
        fetchJson("/api/shift-templates")
    ]);
    const venueEmployees = employees.filter((e) => e.venueId === venueId && e.active);
    const shiftTemplates = allShiftTemplates.filter((t) => t.venueId === venueId);
    return { venueEmployees, shiftTemplates };
}

async function generateWeek(root, venueId, isoYear, isoWeek) {
    setStatusMessage(root, null);
    root.getElementById("uncovered-list").hidden = true;

    const { venueEmployees, shiftTemplates } = await loadReferenceData(venueId);
    const shiftTemplateLabelsById = new Map(shiftTemplates.map((t) => [t.id, formatShiftLabel(t)]));

    const generation = await fetchJson("/api/schedules/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ venueId, isoYear, isoWeek })
    });

    const week = {
        scheduleId: generation.scheduleId,
        days: buildWeekDays(generation.isoYear, generation.isoWeek),
        employees: venueEmployees.map((e) => ({ id: e.id, name: e.name })),
        assignments: generation.assignments.map((a) => ({
            employeeId: a.employeeId,
            date: a.date,
            shiftTemplateId: a.shiftTemplateId
        })),
        shiftTemplates: shiftTemplates.map((t) => ({ id: t.id, label: shiftTemplateLabelsById.get(t.id) }))
    };

    renderSchedule(root, week);
    renderUncoveredSlots(root, generation.uncoveredSlots, shiftTemplateLabelsById);

    currentWeekMeta = { scheduleId: generation.scheduleId, isoYear: generation.isoYear, isoWeek: generation.isoWeek };
    document.getElementById("venue-name").textContent = `Venue #${generation.venueId}`;
    updateWeekLabel(root, generation.status);

    const publishButton = root.getElementById("publish-button");
    publishButton.hidden = false;
    publishButton.disabled = false;

    const hint = generation.uncoveredSlots.length > 0
        ? ` — ${generation.uncoveredSlots.length} hueco(s) sin cubrir, revisa antes de publicar`
        : "";
    setStatusMessage(root, `Cuadrante generado correctamente.${hint}`, generation.uncoveredSlots.length > 0);
}

document.addEventListener("DOMContentLoaded", () => {
    const { isoYear, isoWeek } = currentIsoYearWeek();
    document.getElementById("iso-year-input").value = isoYear;
    document.getElementById("iso-week-input").value = isoWeek;

    document.getElementById("generate-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const button = document.getElementById("generate-button");
        const venueId = Number(document.getElementById("venue-id-input").value);
        const year = Number(document.getElementById("iso-year-input").value);
        const week = Number(document.getElementById("iso-week-input").value);

        button.disabled = true;
        setStatusMessage(document, "Generando…", false);
        try {
            await generateWeek(document, venueId, year, week);
        } catch (error) {
            setStatusMessage(document, error.message, true);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById("publish-button").addEventListener("click", async (event) => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
            const result = await fetchJson(`/api/schedules/${currentWeekMeta.scheduleId}/publish`, { method: "POST" });
            lockScheduleForEditing(document);
            updateWeekLabel(document, result.status);
            button.hidden = true;

            const hasWarnings = result.softWarnings && result.softWarnings.length > 0;
            const warningText = hasWarnings ? ` Aviso: ${result.softWarnings.join("; ")}` : "";
            setStatusMessage(document, `Cuadrante publicado.${warningText}`, hasWarnings);
        } catch (error) {
            setStatusMessage(document, error.message, true);
            button.disabled = false;
        }
    });
});
