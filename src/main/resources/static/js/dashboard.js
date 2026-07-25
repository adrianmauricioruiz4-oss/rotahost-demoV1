/** Panel principal (T4.6): resumen del venue propio tras el login. */
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
    const el = document.getElementById("status-message");
    if (!message) {
        el.hidden = true;
        return;
    }
    el.textContent = message;
    el.className = isError ? "status-message error" : "status-message success";
    el.hidden = false;
}

function renderScheduleStatus(scheduleStatus) {
    const badge = document.getElementById("dash-status-badge");
    const text = document.getElementById("dash-status-text");
    if (scheduleStatus === "PUBLISHED") {
        badge.className = "badge badge-pub";
        text.textContent = "Publicado";
    } else if (scheduleStatus === "DRAFT") {
        badge.className = "badge badge-draft";
        text.textContent = "Borrador";
    } else {
        badge.className = "badge";
        text.textContent = "Sin generar";
    }
}

function renderAlerts(alerts) {
    const section = document.getElementById("dash-alerts-section");
    const list = document.getElementById("dash-alerts-list");
    list.innerHTML = "";

    if (!alerts || alerts.length === 0) {
        section.hidden = true;
        return;
    }
    section.hidden = false;
    alerts.forEach((alert) => {
        const item = document.createElement("li");
        item.textContent = alert;
        list.appendChild(item);
    });
}

async function loadSummary() {
    setStatusMessage(null);
    const summary = await fetchJson("/api/dashboard/summary");

    document.getElementById("dash-venue-name").textContent = summary.venueName;
    document.getElementById("dash-employee-count").textContent = summary.employeeCount;
    document.getElementById("dash-week-label").textContent = `Cuadrante · semana ${summary.isoWeek}/${summary.isoYear}`;
    renderScheduleStatus(summary.scheduleStatus);
    renderAlerts(summary.alerts);

    document.getElementById("dash-stats").hidden = false;
    if (typeof window.updateShellVenueName === "function") {
        window.updateShellVenueName(summary.venueName);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    fetchJson("/api/auth/me")
        .then((me) => {
            if (me.role !== "MANAGER") {
                window.location.href = "employee.html";
                return;
            }
            return loadSummary();
        })
        .catch((error) => setStatusMessage(error.message, true));
});
