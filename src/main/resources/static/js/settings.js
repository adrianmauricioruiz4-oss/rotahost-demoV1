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
    el.className = isError ? "status-message error" : "status-message success";
    el.hidden = false;
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
    const venueTemplates = shiftTemplates.filter((t) => t.venueId === venueId);
    renderShiftTemplates(venueTemplates, venueId);
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
    card.className = "shift-template-card generate-form";

    const nameLabel = document.createElement("label");
    nameLabel.textContent = "Nombre";
    const nameInput = document.createElement("input");
    nameInput.type = "text";
    nameInput.required = true;
    nameInput.value = template.name;
    nameLabel.appendChild(nameInput);
    card.appendChild(nameLabel);

    const segmentInputs = template.segments.map((segment, index) => {
        const startLabel = document.createElement("label");
        startLabel.textContent = `Tramo ${index + 1} — inicio`;
        const startInput = document.createElement("input");
        startInput.type = "time";
        startInput.required = true;
        startInput.value = toTimeInputValue(segment.startTime);
        startLabel.appendChild(startInput);

        const endLabel = document.createElement("label");
        endLabel.textContent = `Tramo ${index + 1} — fin`;
        const endInput = document.createElement("input");
        endInput.type = "time";
        endInput.required = true;
        endInput.value = toTimeInputValue(segment.endTime);
        endLabel.appendChild(endInput);

        card.appendChild(startLabel);
        card.appendChild(endLabel);
        return { startInput, endInput };
    });

    const saveButton = document.createElement("button");
    saveButton.type = "submit";
    saveButton.textContent = "Guardar";
    card.appendChild(saveButton);

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

async function loadVenueAndShifts(venueId) {
    setStatusMessage(null);
    await loadVenue(venueId);
    await loadShiftTemplates(venueId);
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("venue-lookup-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const venueId = Number(document.getElementById("venue-id-input").value);
        try {
            await loadVenueAndShifts(venueId);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    document.getElementById("venue-edit-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const venueId = Number(document.getElementById("venue-id-input").value);
        const body = {
            name: document.getElementById("venue-name-input").value,
            openingTime: document.getElementById("venue-opening-input").value,
            closingTime: document.getElementById("venue-closing-input").value
        };
        try {
            await fetchJson(`/api/venues/${venueId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage("Horario del local actualizado.", false);
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });

    const initialVenueId = Number(document.getElementById("venue-id-input").value);
    loadVenueAndShifts(initialVenueId).catch((error) => setStatusMessage(error.message, true));
});
