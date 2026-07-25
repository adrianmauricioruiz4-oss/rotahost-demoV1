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
    el.className = isError ? "status-message error" : "status-message success";
    el.hidden = false;
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
    form.className = "employee-card generate-form";

    const nameLabel = document.createElement("label");
    nameLabel.textContent = "Nombre";
    const nameInput = document.createElement("input");
    nameInput.type = "text";
    nameInput.required = true;
    nameInput.value = employee ? employee.name : "";
    nameLabel.appendChild(nameInput);
    form.appendChild(nameLabel);

    const emailLabel = document.createElement("label");
    emailLabel.textContent = "Email";
    const emailInput = document.createElement("input");
    emailInput.type = "email";
    emailInput.required = true;
    emailInput.value = employee ? employee.email : "";
    emailLabel.appendChild(emailInput);
    form.appendChild(emailLabel);

    const contractLabel = document.createElement("label");
    contractLabel.textContent = "Contrato";
    const contractSelect = document.createElement("select");
    ["FULL_TIME", "PART_TIME"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value === "FULL_TIME" ? "Jornada completa" : "Parcial";
        contractSelect.appendChild(option);
    });
    contractSelect.value = employee ? employee.contractType : "FULL_TIME";
    contractLabel.appendChild(contractSelect);
    form.appendChild(contractLabel);

    const hoursLabel = document.createElement("label");
    hoursLabel.textContent = "Horas/semana";
    const hoursInput = document.createElement("input");
    hoursInput.type = "number";
    hoursInput.min = "1";
    hoursInput.value = employee && employee.contractHours ? employee.contractHours : "";
    hoursLabel.appendChild(hoursInput);
    form.appendChild(hoursLabel);

    function updateHoursVisibility() {
        hoursLabel.hidden = contractSelect.value !== "PART_TIME";
    }
    contractSelect.addEventListener("change", updateHoursVisibility);
    updateHoursVisibility();

    const positionsFieldset = document.createElement("fieldset");
    const positionsLegend = document.createElement("legend");
    positionsLegend.textContent = "Puestos";
    positionsFieldset.appendChild(positionsLegend);
    const employeePositions = new Set(employee ? employee.positions : []);
    const positionCheckboxes = Object.entries(POSITION_LABELS).map(([value, label]) => {
        const checkboxLabel = document.createElement("label");
        checkboxLabel.className = "checkbox-label";
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

    const minEntryLabel = document.createElement("label");
    minEntryLabel.textContent = "Hora mínima de entrada";
    const minEntryInput = document.createElement("input");
    minEntryInput.type = "time";
    minEntryInput.value = employee ? toTimeInputValue(employee.minEntryTime) : "";
    minEntryLabel.appendChild(minEntryInput);
    form.appendChild(minEntryLabel);

    const maxExitLabel = document.createElement("label");
    maxExitLabel.textContent = "Hora máxima de salida";
    const maxExitInput = document.createElement("input");
    maxExitInput.type = "time";
    maxExitInput.value = employee ? toTimeInputValue(employee.maxExitTime) : "";
    maxExitLabel.appendChild(maxExitInput);
    form.appendChild(maxExitLabel);

    const notesLabel = document.createElement("label");
    notesLabel.textContent = "Notas internas";
    const notesInput = document.createElement("textarea");
    notesInput.value = employee && employee.internalNotes ? employee.internalNotes : "";
    notesLabel.appendChild(notesInput);
    form.appendChild(notesLabel);

    const saveButton = document.createElement("button");
    saveButton.type = "submit";
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
    label.className = "checkbox-label";
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
    const wrapper = document.createElement("div");
    wrapper.className = "employee-card-wrapper";

    const details = document.createElement("details");
    details.className = "employee-details";

    const summary = document.createElement("summary");
    const summaryName = document.createElement("span");
    summaryName.className = "employee-summary-name";
    summaryName.textContent = employee.name;
    if (!employee.active) {
        const inactiveBadge = document.createElement("span");
        inactiveBadge.className = "employee-inactive-badge";
        inactiveBadge.textContent = "inactivo";
        summaryName.appendChild(inactiveBadge);
    }
    const summaryMeta = document.createElement("span");
    summaryMeta.className = "employee-summary-meta";
    summaryMeta.textContent = employeeSummaryLine(employee);
    summary.appendChild(summaryName);
    summary.appendChild(summaryMeta);
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
    details.appendChild(form);
    wrapper.appendChild(details);

    if (employee.active) {
        const deactivateButton = document.createElement("button");
        deactivateButton.type = "button";
        deactivateButton.className = "preference-delete-button employee-deactivate-button";
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
        wrapper.appendChild(deactivateButton);
    }

    return wrapper;
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
