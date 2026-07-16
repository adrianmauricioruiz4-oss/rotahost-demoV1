// Datos de ejemplo para poder ver la tabla funcionando ya. T3.2 sustituye
// esto por una llamada real a POST /api/schedules/generate.
const SAMPLE_WEEK = {
    venueName: "Bar La Esquina",
    isoYear: 2026,
    isoWeek: 30,
    days: [
        { date: "2026-07-20", label: "Lun 20" },
        { date: "2026-07-21", label: "Mar 21" },
        { date: "2026-07-22", label: "Mié 22" },
        { date: "2026-07-23", label: "Jue 23" },
        { date: "2026-07-24", label: "Vie 24" },
        { date: "2026-07-25", label: "Sáb 25" },
        { date: "2026-07-26", label: "Dom 26" }
    ],
    employees: [
        { id: 1, name: "Ana García" },
        { id: 2, name: "Javier Martínez" },
        { id: 3, name: "Laura Fernández" },
        { id: 4, name: "David Sánchez" }
    ],
    assignments: [
        { employeeId: 1, date: "2026-07-20", shiftName: "MAÑANA" },
        { employeeId: 1, date: "2026-07-21", shiftName: "MAÑANA" },
        { employeeId: 2, date: "2026-07-20", shiftName: "TARDE" },
        { employeeId: 2, date: "2026-07-24", shiftName: "TARDE" },
        { employeeId: 3, date: "2026-07-22", shiftName: "PARTIDO" },
        { employeeId: 4, date: "2026-07-25", shiftName: "TARDE" },
        { employeeId: 4, date: "2026-07-26", shiftName: "MAÑANA" }
    ]
};

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

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("venue-name").textContent = SAMPLE_WEEK.venueName;
    document.getElementById("week-label").textContent =
        `Semana ISO ${SAMPLE_WEEK.isoWeek} · ${SAMPLE_WEEK.isoYear} (datos de ejemplo — T3.2 conectará esto a la API real)`;
    renderSchedule(document, SAMPLE_WEEK);
});
