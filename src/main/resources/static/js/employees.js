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

const POSITION_LABELS = {
    CAMARERO: "Camarero/a",
    COCINERO: "Cocinero/a",
    AYUDANTE_COCINA: "Ayudante de cocina",
    RESPONSABLE_SALA: "Responsable de sala",
    ENCARGADO: "Encargado/a",
    REPARTIDOR: "Repartidor/a"
};

let currentVenueId = null;

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

/** label + input de texto/número/hora/email, con la clase .field del sistema de diseño. */
function buildField(labelText, input) {
    const field = document.createElement("div");
    field.className = "field";
    const label = document.createElement("label");
    label.className = "label";
    const inputId = `f-${labelText.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${Math.random().toString(36).slice(2, 7)}`;
    label.htmlFor = inputId;
    label.textContent = labelText;
    input.id = inputId;
    input.classList.add(input.tagName === "SELECT" ? "select" : input.tagName === "TEXTAREA" ? "textarea" : "input");
    field.appendChild(label);
    field.appendChild(input);
    return field;
}

/** "08:00:00"/null -> "08:00"/"" (formato que aceptan los <input type="time">). */
function toTimeInputValue(localTime) {
    return localTime ? localTime.slice(0, 5) : "";
}

/**
 * Construye el formulario de alta/edición. employee es null para el alta; con datos, para editar.
 * onSubmit recibe el body ya listo para POST/PUT.
 */
function buildEmployeeForm(employee, submitLabel, onSubmit) {
    const form = document.createElement("form");
    form.className = "stack-4";

    const nameInput = document.createElement("input");
    nameInput.type = "text";
    nameInput.required = true;
    nameInput.value = employee ? employee.name : "";
    form.appendChild(buildField("Nombre", nameInput));

    const emailInput = document.createElement("input");
    emailInput.type = "email";
    emailInput.required = true;
    emailInput.value = employee ? employee.email : "";
    form.appendChild(buildField("Email", emailInput));

    const contractSelect = document.createElement("select");
    ["FULL_TIME", "PART_TIME"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value === "FULL_TIME" ? "Jornada completa" : "Parcial";
        contractSelect.appendChild(option);
    });
    contractSelect.value = employee ? employee.contractType : "FULL_TIME";
    form.appendChild(buildField("Contrato", contractSelect));

    const hoursInput = document.createElement("input");
    hoursInput.type = "number";
    hoursInput.min = "1";
    hoursInput.value = employee && employee.contractHours ? employee.contractHours : "";
    const hoursField = buildField("Horas/semana", hoursInput);
    form.appendChild(hoursField);

    function updateHoursVisibility() {
        hoursField.hidden = contractSelect.value !== "PART_TIME";
    }
    contractSelect.addEventListener("change", updateHoursVisibility);
    updateHoursVisibility();

    const positionsFieldset = document.createElement("fieldset");
    positionsFieldset.className = "checkbox-group";
    const positionsLegend = document.createElement("legend");
    positionsLegend.textContent = "Puestos";
    positionsFieldset.appendChild(positionsLegend);
    const employeePositions = new Set(employee ? employee.positions : []);
    const positionCheckboxes = Object.entries(POSITION_LABELS).map(([value, label]) => {
        const checkboxLabel = document.createElement("label");
        checkboxLabel.className = "checkbox-row";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.value = value;
        checkbox.defaultChecked = employeePositions.has(value);
        checkboxLabel.appendChild(checkbox);
        checkboxLabel.appendChild(document.createTextNode(label));
        positionsFieldset.appendChild(checkboxLabel);
        return checkbox;
    });
    form.appendChild(positionsFieldset);

    const capabilitiesFieldset = document.createElement("fieldset");
    capabilitiesFieldset.className = "checkbox-group";
    const capabilitiesLegend = document.createElement("legend");
    capabilitiesLegend.textContent = "Capacidades";
    capabilitiesFieldset.appendChild(capabilitiesLegend);
    const splitShiftCheckbox = buildCapabilityCheckbox(
        capabilitiesFieldset, "Puede turno partido", employee ? employee.canWorkSplitShift : true);
    const openCheckbox = buildCapabilityCheckbox(
        capabilitiesFieldset, "Puede apertura", employee ? employee.canOpen : true);
    const closeCheckbox = buildCapabilityCheckbox(
        capabilitiesFieldset, "Puede cierre", employee ? employee.canClose : true);
    form.appendChild(capabilitiesFieldset);

    const minEntryInput = document.createElement("input");
    minEntryInput.type = "time";
    minEntryInput.value = employee ? toTimeInputValue(employee.minEntryTime) : "";
    form.appendChild(buildField("Hora mínima de entrada", minEntryInput));

    const maxExitInput = document.createElement("input");
    maxExitInput.type = "time";
    maxExitInput.value = employee ? toTimeInputValue(employee.maxExitTime) : "";
    form.appendChild(buildField("Hora máxima de salida", maxExitInput));

    const notesInput = document.createElement("textarea");
    notesInput.value = employee && employee.internalNotes ? employee.internalNotes : "";
    form.appendChild(buildField("Notas internas", notesInput));

    const saveButton = document.createElement("button");
    saveButton.type = "submit";
    saveButton.className = "btn btn--primary";
    saveButton.textContent = submitLabel;
    form.appendChild(saveButton);

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const body = {
            name: nameInput.value,
            email: emailInput.value,
            contractType: contractSelect.value,
            contractHours: contractSelect.value === "PART_TIME" ? Number(hoursInput.value) : null,
            venueId: currentVenueId,
            positions: positionCheckboxes.filter((c) => c.checked).map((c) => c.value),
            canWorkSplitShift: splitShiftCheckbox.checked,
            canOpen: openCheckbox.checked,
            canClose: closeCheckbox.checked,
            minEntryTime: minEntryInput.value || null,
            maxExitTime: maxExitInput.value || null,
            internalNotes: notesInput.value || null
        };
        await onSubmit(body);
    });

    return form;
}

function buildCapabilityCheckbox(fieldset, text, checked) {
    const label = document.createElement("label");
    label.className = "checkbox-row";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.defaultChecked = checked;
    label.appendChild(checkbox);
    label.appendChild(document.createTextNode(text));
    fieldset.appendChild(label);
    return checkbox;
}

async function loadEmployees() {
    const employees = await fetchJson("/api/employees");
    renderEmployees(employees);
}

function renderEmployees(employees) {
    const list = document.getElementById("employees-list");
    const empty = document.getElementById("employees-empty");
    list.innerHTML = "";

    if (employees.length === 0) {
        empty.hidden = false;
        return;
    }
    empty.hidden = true;

    employees.forEach((employee) => {
        list.appendChild(buildEmployeeCard(employee));
    });
}

/** Resumen corto para no obligar a abrir la tarjeta solo para ver quién es quién. */
function employeeSummaryLine(employee) {
    const contract = employee.contractType === "PART_TIME"
        ? `Parcial${employee.contractHours ? ` (${employee.contractHours}h/sem)` : ""}`
        : "Jornada completa";
    const positions = employee.positions && employee.positions.length > 0
        ? employee.positions.map((value) => POSITION_LABELS[value] || value).join(", ")
        : "Sin puesto asignado";
    return `${contract} · ${positions}`;
}

function buildEmployeeCard(employee) {
    const details = document.createElement("details");
    details.className = "expand-card";

    const summary = document.createElement("summary");
    const summaryText = document.createElement("span");
    const summaryName = document.createElement("span");
    summaryName.className = "es-name";
    summaryName.textContent = employee.name;
    summaryText.appendChild(summaryName);
    const summaryMeta = document.createElement("span");
    summaryMeta.className = "es-meta";
    summaryMeta.textContent = employeeSummaryLine(employee);
    summaryText.appendChild(summaryMeta);
    summary.appendChild(summaryText);
    if (!employee.active) {
        const inactiveBadge = document.createElement("span");
        inactiveBadge.className = "pill pill--off";
        inactiveBadge.textContent = "Inactivo";
        summary.appendChild(inactiveBadge);
    }
    details.appendChild(summary);

    const form = buildEmployeeForm(employee, "Guardar", async (body) => {
        try {
            await fetchJson(`/api/employees/${employee.id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage(`"${body.name}" actualizado.`, false);
            await loadEmployees();
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });
    form.classList.add("expand-body");
    details.appendChild(form);

    if (employee.active) {
        const footer = document.createElement("div");
        footer.className = "expand-footer";
        const deactivateButton = document.createElement("button");
        deactivateButton.type = "button";
        deactivateButton.className = "btn btn--danger btn--sm";
        deactivateButton.textContent = "Dar de baja";
        deactivateButton.addEventListener("click", async () => {
            if (!window.confirm(`¿Dar de baja a ${employee.name}?`)) {
                return;
            }
            try {
                await fetchJson(`/api/employees/${employee.id}`, { method: "DELETE" });
                setStatusMessage(`"${employee.name}" dado de baja.`, false);
                await loadEmployees();
            } catch (error) {
                setStatusMessage(error.message, true);
            }
        });
        footer.appendChild(deactivateButton);
        details.appendChild(footer);
    }

    return details;
}

function logout() {
    fetchJson("/logout", { method: "POST" })
        .catch(() => {})
        .finally(() => { window.location.href = "/"; });
}

document.addEventListener("DOMContentLoaded", () => {
    const newEmployeeForm = buildEmployeeForm(null, "Añadir empleado", async (body) => {
        try {
            await fetchJson("/api/employees", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            setStatusMessage(`"${body.name}" añadido.`, false);
            newEmployeeForm.reset();
            newEmployeeForm.querySelector("select").dispatchEvent(new Event("change"));
            await loadEmployees();
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });
    newEmployeeForm.id = "new-employee-form";
    newEmployeeForm.classList.add("expand-body");
    document.getElementById("new-employee-form").replaceWith(newEmployeeForm);

    fetchJson("/api/auth/me").then((me) => {
        if (me.role !== "MANAGER") {
            window.location.href = "employee.html";
            return;
        }
        currentVenueId = me.venueId;
        return loadEmployees();
    }).catch((error) => setStatusMessage(error.message, true));
});
