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

let currentVenueId = null;

/** "08:00:00" -> "08:00" (formato que aceptan los <input type="time">). */
function toTimeInputValue(localTime) {
    return localTime.slice(0, 5);
}

function setStatusMessage(message, isError) {
    const el = document.getElementById("status-message");
    if (!message) {
        el.hidden = true;
        return;
    }
    el.textContent = message;
    el.className = "notice " + (isError ? "notice--alert" : "notice--ok");
    el.hidden = false;
}

/** label + input, con la clase .field del sistema de diseño. */
function buildField(labelText, input) {
    const field = document.createElement("div");
    field.className = "field";
    const label = document.createElement("label");
    label.className = "label";
    const inputId = `f-${labelText.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${Math.random().toString(36).slice(2, 7)}`;
    label.htmlFor = inputId;
    label.textContent = labelText;
    input.id = inputId;
    input.classList.add("input");
    field.appendChild(label);
    field.appendChild(input);
    return field;
}

async function loadVenue(venueId) {
    const venue = await fetchJson(`/api/venues/${venueId}`);
    document.getElementById("venue-name-input").value = venue.name;
    document.getElementById("venue-opening-input").value = toTimeInputValue(venue.openingTime);
    document.getElementById("venue-closing-input").value = toTimeInputValue(venue.closingTime);
    document.getElementById("venue-edit-form").hidden = false;
}

async function loadShiftTemplates(venueId) {
    const shiftTemplates = await fetchJson("/api/shift-templates");
    const venueTemplates = shiftTemplates.filter((t) => t.venueId === venueId && t.active);
    renderShiftTemplates(venueTemplates, venueId);
    return venueTemplates;
}

function renderShiftTemplates(templates, venueId) {
    const list = document.getElementById("shift-templates-list");
    const empty = document.getElementById("shift-templates-empty");
    list.innerHTML = "";

    if (templates.length === 0) {
        empty.hidden = false;
        return;
    }
    empty.hidden = true;

    templates.forEach((template) => {
        list.appendChild(buildShiftTemplateCard(template, venueId));
    });
}

function buildShiftTemplateCard(template, venueId) {
    const card = document.createElement("form");
    card.className = "surface stack-4";
    card.style.maxWidth = "480px";

    const nameInput = document.createElement("input");
    nameInput.type = "text";
    nameInput.required = true;
    nameInput.value = template.name;
    card.appendChild(buildField("Nombre", nameInput));

    const segmentInputs = template.segments.map((segment, index) => {
        const startInput = document.createElement("input");
        startInput.type = "time";
        startInput.required = true;
        startInput.value = toTimeInputValue(segment.startTime);
        card.appendChild(buildField(`Tramo ${index + 1} — inicio`, startInput));

        const endInput = document.createElement("input");
        endInput.type = "time";
        endInput.required = true;
        endInput.value = toTimeInputValue(segment.endTime);
        card.appendChild(buildField(`Tramo ${index + 1} — fin`, endInput));

        return { startInput, endInput };
    });

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.textContent = "Eliminar franja";
    deleteButton.className = "btn btn--danger";
    deleteButton.addEventListener("click", async () => {
        if (!window.confirm(`¿Eliminar la franja "${template.name}"? Se quitará también su cobertura mínima.`)) {
            return;
        }
        try {
            await fetchJson(`/api/shift-templates/${template.id}`, { method: "DELETE" });
            setStatusMessage(`Franja "${template.name}" eliminada.`, false);
            await loadShiftTemplatesAndCoverage(venueId);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    const saveButton = document.createElement("button");
    saveButton.type = "submit";
    saveButton.className = "btn btn--primary";
    saveButton.textContent = "Guardar";

    const actions = document.createElement("div");
    actions.className = "row-end";
    actions.appendChild(deleteButton);
    actions.appendChild(saveButton);
    card.appendChild(actions);

    card.addEventListener("submit", async (event) => {
        event.preventDefault();
        const body = {
            name: nameInput.value,
            venueId,
            segments: segmentInputs.map(({ startInput, endInput }) => ({
                startTime: startInput.value,
                endTime: endInput.value
            }))
        };
        try {
            await fetchJson(`/api/shift-templates/${template.id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage(`Turno "${nameInput.value}" actualizado.`, false);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    return card;
}

const DAYS_OF_WEEK = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

async function loadShiftTemplatesAndCoverage(venueId) {
    const templates = await loadShiftTemplates(venueId);
    await loadCoverage(venueId, templates);
}

async function loadCoverage(venueId, templates) {
    const wrap = document.getElementById("coverage-wrap");
    const empty = document.getElementById("coverage-empty");

    if (templates.length === 0) {
        wrap.hidden = true;
        empty.hidden = false;
        return;
    }
    empty.hidden = true;
    wrap.hidden = false;

    const requirements = (await fetchJson("/api/coverage-requirements"))
        .filter((r) => r.venueId === venueId && r.position === null);
    renderCoverageTable(templates, requirements, venueId);
}

function renderCoverageTable(templates, requirements, venueId) {
    const body = document.getElementById("coverage-table-body");
    body.innerHTML = "";

    templates.forEach((template) => {
        const row = document.createElement("tr");

        const nameCell = document.createElement("td");
        nameCell.textContent = template.name;
        row.appendChild(nameCell);

        DAYS_OF_WEEK.forEach((dayOfWeek) => {
            const cell = document.createElement("td");
            const existing = requirements.find(
                (r) => r.shiftTemplateId === template.id && r.dayOfWeek === dayOfWeek);

            const input = document.createElement("input");
            input.type = "number";
            input.min = "0";
            input.className = "input input--cell";
            input.value = existing ? existing.requiredCount : "";
            input.dataset.requirementId = existing ? existing.id : "";

            input.addEventListener("change", async () => {
                await saveCoverageCell(input, template.id, dayOfWeek, venueId);
            });

            cell.appendChild(input);
            row.appendChild(cell);
        });

        body.appendChild(row);
    });
}

async function saveCoverageCell(input, shiftTemplateId, dayOfWeek, venueId) {
    const requirementId = input.dataset.requirementId || null;
    const count = parseInt(input.value, 10);

    try {
        if ((isNaN(count) || count <= 0)) {
            if (requirementId) {
                await fetchJson(`/api/coverage-requirements/${requirementId}`, { method: "DELETE" });
                input.dataset.requirementId = "";
            }
            input.value = "";
            setStatusMessage("Cobertura eliminada.", false);
            return;
        }

        const body = { venueId, dayOfWeek, shiftTemplateId, requiredCount: count, position: null };
        if (requirementId) {
            await fetchJson(`/api/coverage-requirements/${requirementId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage("Cobertura actualizada.", false);
        } else {
            const created = await fetchJson("/api/coverage-requirements", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            input.dataset.requirementId = created.id;
            setStatusMessage("Cobertura guardada.", false);
        }
    } catch (error) {
        setStatusMessage(error.message, true);
    }
}

async function loadVenueAndShifts(venueId) {
    setStatusMessage(null);
    await loadVenue(venueId);
    await loadShiftTemplatesAndCoverage(venueId);
}

function logout() {
    fetchJson("/logout", { method: "POST" })
        .catch(() => {})
        .finally(() => { window.location.href = "/"; });
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("venue-edit-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const body = {
            name: document.getElementById("venue-name-input").value,
            openingTime: document.getElementById("venue-opening-input").value,
            closingTime: document.getElementById("venue-closing-input").value
        };
        try {
            await fetchJson(`/api/venues/${currentVenueId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage("Horario del local actualizado.", false);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    document.getElementById("create-shift-template-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const nameInput = document.getElementById("new-shift-name-input");
        const startInput = document.getElementById("new-shift-start-input");
        const endInput = document.getElementById("new-shift-end-input");
        const body = {
            name: nameInput.value,
            venueId: currentVenueId,
            segments: [{ startTime: startInput.value, endTime: endInput.value }]
        };
        try {
            await fetchJson("/api/shift-templates", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage(`Franja "${nameInput.value}" creada.`, false);
            event.target.reset();
            await loadShiftTemplatesAndCoverage(currentVenueId);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    fetchJson("/api/auth/me").then((me) => {
        if (me.role !== "MANAGER") {
            window.location.href = "employee.html";
            return;
        }
        currentVenueId = me.venueId;
        return loadVenueAndShifts(currentVenueId);
    }).catch((error) => setStatusMessage(error.message, true));
});
