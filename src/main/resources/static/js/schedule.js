const DAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

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
            const shiftName = employeeAssignments.get(day.date);

            const badge = document.createElement("span");
            badge.className = shiftName ? "shift-cell" : "shift-cell empty";
            badge.textContent = shiftName || "—";
            cell.appendChild(badge);

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
        map.get(assignment.employeeId).set(assignment.date, assignment.shiftName);
    });
    return map;
}

function renderUncoveredSlots(root, uncoveredSlots, shiftTemplateNamesById) {
    const list = root.getElementById("uncovered-list");
    list.innerHTML = "";
    if (!uncoveredSlots || uncoveredSlots.length === 0) {
        list.hidden = true;
        return;
    }
    uncoveredSlots.forEach((slot) => {
        const shiftName = shiftTemplateNamesById.get(slot.shiftTemplateId) || `turno #${slot.shiftTemplateId}`;
        const item = document.createElement("li");
        item.textContent = `${slot.date} · ${shiftName} · faltan ${slot.missing}`;
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

async function loadReferenceData(venueId) {
    const [employees, shiftTemplates] = await Promise.all([
        fetchJson("/api/employees"),
        fetchJson("/api/shift-templates")
    ]);
    const venueEmployees = employees.filter((e) => e.venueId === venueId && e.active);
    const shiftTemplateNamesById = new Map(
        shiftTemplates.filter((t) => t.venueId === venueId).map((t) => [t.id, t.name])
    );
    return { venueEmployees, shiftTemplateNamesById };
}

async function generateWeek(root, venueId, isoYear, isoWeek) {
    setStatusMessage(root, null);
    root.getElementById("uncovered-list").hidden = true;

    const { venueEmployees, shiftTemplateNamesById } = await loadReferenceData(venueId);

    const generation = await fetchJson("/api/schedules/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ venueId, isoYear, isoWeek })
    });

    const week = {
        isoYear: generation.isoYear,
        isoWeek: generation.isoWeek,
        days: buildWeekDays(generation.isoYear, generation.isoWeek),
        employees: venueEmployees.map((e) => ({ id: e.id, name: e.name })),
        assignments: generation.assignments.map((a) => ({
            employeeId: a.employeeId,
            date: a.date,
            shiftName: shiftTemplateNamesById.get(a.shiftTemplateId) || `turno #${a.shiftTemplateId}`
        }))
    };

    renderSchedule(root, week);
    renderUncoveredSlots(root, generation.uncoveredSlots, shiftTemplateNamesById);

    document.getElementById("venue-name").textContent = `Venue #${generation.venueId}`;
    document.getElementById("week-label").textContent =
        `Semana ISO ${generation.isoWeek} · ${generation.isoYear} — cuadrante #${generation.scheduleId} (${generation.status})`;

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
});
