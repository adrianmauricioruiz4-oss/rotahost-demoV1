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
let fieldSeq = 0;

/** "08:00:00" -> "08:00" (formato que aceptan los <input type="time">). */
function toTimeInputValue(localTime) {
    return localTime.slice(0, 5);
}

function setStatusMessage(message, isError) {
    showNotice("status-message", message, isError ? "alert" : "ok");
}

/** Campo con su label asociado por id, como exige la accesibilidad mínima de DESIGN.md. */
function buildField(labelText, control) {
    const field = document.createElement("div");
    field.className = "field";

    fieldSeq += 1;
    control.id = `shift-field-${fieldSeq}`;

    const label = document.createElement("label");
    label.className = "label";
    label.htmlFor = control.id;
    label.textContent = labelText;

    field.appendChild(label);
    field.appendChild(control);
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
    renderShiftTemplates(shiftTemplates.filter((t) => t.venueId === venueId), venueId);
}

/** "08:00–16:00 y 20:00–00:00" — todos los tramos del turno en una línea. */
function segmentsLine(template) {
    return template.segments
        .map((segment) => `${toTimeInputValue(segment.startTime)}–${toTimeInputValue(segment.endTime)}`)
        .join(" y ");
}

function renderShiftTemplates(templates, venueId) {
    const list = document.getElementById("shift-templates-list");
    const empty = document.getElementById("shift-templates-empty");
    const table = document.getElementById("shift-templates-table");
    list.replaceChildren();

    const isEmpty = templates.length === 0;
    empty.hidden = !isEmpty;
    table.hidden = isEmpty;
    if (isEmpty) {
        return;
    }
    templates.forEach((template) => list.appendChild(buildShiftTemplateRow(template, venueId)));
}

/** El nombre del turno lo escribe el encargado: se inserta con textContent, nunca como HTML. */
function buildShiftTemplateRow(template, venueId) {
    const row = document.createElement("tr");

    const nameCell = document.createElement("td");
    const name = document.createElement("div");
    name.className = "cell-title";
    name.textContent = template.name;
    nameCell.appendChild(name);
    row.appendChild(nameCell);

    const scheduleCell = document.createElement("td");
    scheduleCell.textContent = segmentsLine(template);
    row.appendChild(scheduleCell);

    const actionsCell = document.createElement("td");
    actionsCell.className = "right";
    const actions = document.createElement("div");
    actions.className = "row-actions";

    const editButton = document.createElement("button");
    editButton.type = "button";
    editButton.className = "btn btn--secondary btn--sm";
    editButton.textContent = "Editar";
    editButton.addEventListener("click", () => openShiftTemplateForm(template, venueId));
    actions.appendChild(editButton);

    actionsCell.appendChild(actions);
    row.appendChild(actionsCell);
    return row;
}

/** Edita el nombre y las horas de cada tramo del turno. El número de tramos no cambia aquí. */
function openShiftTemplateForm(template, venueId) {
    const form = document.createElement("form");

    const nameInput = document.createElement("input");
    nameInput.className = "input";
    nameInput.type = "text";
    nameInput.required = true;
    nameInput.value = template.name;
    form.appendChild(buildField("Nombre del turno", nameInput));

    const single = template.segments.length === 1;
    const segmentInputs = template.segments.map((segment, index) => {
        const suffix = single ? "" : ` del tramo ${index + 1}`;

        const startInput = document.createElement("input");
        startInput.className = "input";
        startInput.type = "time";
        startInput.required = true;
        startInput.value = toTimeInputValue(segment.startTime);
        form.appendChild(buildField(`Hora de entrada${suffix}`, startInput));

        const endInput = document.createElement("input");
        endInput.className = "input";
        endInput.type = "time";
        endInput.required = true;
        endInput.value = toTimeInputValue(segment.endTime);
        form.appendChild(buildField(`Hora de salida${suffix}`, endInput));

        return { startInput, endInput };
    });

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
            closeModal();
            setStatusMessage(`Turno ${nameInput.value} actualizado.`, false);
            await loadShiftTemplates(venueId);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    openModal("Editar turno", form);
}

async function loadVenueAndShifts(venueId) {
    setStatusMessage(null);
    await loadVenue(venueId);
    await loadShiftTemplates(venueId);
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
            if (typeof window.updateShellVenueName === "function") {
                window.updateShellVenueName(body.name);
            }
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
