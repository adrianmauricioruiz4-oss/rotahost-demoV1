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
let fieldSeq = 0;

function setStatusMessage(message, isError) {
    showNotice("status-message", message, isError ? "alert" : "ok");
}

/** "08:00:00"/null -> "08:00"/"" (formato que aceptan los <input type="time">). */
function toTimeInputValue(localTime) {
    return localTime ? localTime.slice(0, 5) : "";
}

/** Campo con su label asociado por id, como exige la accesibilidad mínima de DESIGN.md. */
function buildField(labelText, control, hintText) {
    const field = document.createElement("div");
    field.className = "field";

    fieldSeq += 1;
    control.id = `emp-field-${fieldSeq}`;

    const label = document.createElement("label");
    label.className = "label";
    label.htmlFor = control.id;
    label.textContent = labelText;

    field.appendChild(label);
    field.appendChild(control);

    if (hintText) {
        const hint = document.createElement("p");
        hint.className = "hint";
        hint.textContent = hintText;
        field.appendChild(hint);
    }
    return field;
}

function buildCheck(text, checked) {
    const label = document.createElement("label");
    label.className = "check";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.defaultChecked = checked;
    label.appendChild(checkbox);
    label.appendChild(document.createTextNode(text));
    return { label, checkbox };
}

function buildCheckGroup(legendText, entries) {
    const fieldset = document.createElement("fieldset");
    fieldset.className = "fieldset field";

    const legend = document.createElement("legend");
    legend.textContent = legendText;
    fieldset.appendChild(legend);

    const checkboxes = entries.map((entry) => {
        const { label, checkbox } = buildCheck(entry.label, entry.checked);
        checkbox.value = entry.value || "";
        fieldset.appendChild(label);
        return checkbox;
    });
    return { fieldset, checkboxes };
}

/**
 * Construye la ficha de alta/edición para abrirla en un modal. employee es null para el
 * alta; con datos, para editar. onSubmit recibe el body ya listo para POST/PUT.
 */
function buildEmployeeForm(employee, submitLabel, onSubmit) {
    const form = document.createElement("form");

    const nameInput = document.createElement("input");
    nameInput.className = "input";
    nameInput.type = "text";
    nameInput.required = true;
    nameInput.value = employee ? employee.name : "";
    form.appendChild(buildField("Nombre", nameInput));

    const emailInput = document.createElement("input");
    emailInput.className = "input";
    emailInput.type = "email";
    emailInput.required = true;
    emailInput.value = employee ? employee.email : "";
    form.appendChild(buildField("Correo electrónico", emailInput));

    const contractSelect = document.createElement("select");
    contractSelect.className = "select";
    [["FULL_TIME", "Jornada completa"], ["PART_TIME", "Parcial"]].forEach(([value, label]) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label;
        contractSelect.appendChild(option);
    });
    contractSelect.value = employee ? employee.contractType : "FULL_TIME";
    form.appendChild(buildField("Contrato", contractSelect));

    const hoursInput = document.createElement("input");
    hoursInput.className = "input";
    hoursInput.type = "number";
    hoursInput.min = "1";
    hoursInput.value = employee && employee.contractHours ? employee.contractHours : "";
    const hoursField = buildField("Horas por semana", hoursInput, "Según el contrato vigente");
    form.appendChild(hoursField);

    function updateHoursVisibility() {
        hoursField.hidden = contractSelect.value !== "PART_TIME";
    }
    contractSelect.addEventListener("change", updateHoursVisibility);
    updateHoursVisibility();

    const employeePositions = new Set(employee ? employee.positions : []);
    const positions = buildCheckGroup("Puestos", Object.entries(POSITION_LABELS).map(([value, label]) => ({
        value,
        label,
        checked: employeePositions.has(value)
    })));
    form.appendChild(positions.fieldset);

    const capabilities = buildCheckGroup("Capacidades", [
        { label: "Puede hacer turno partido", checked: employee ? employee.canWorkSplitShift : true },
        { label: "Puede abrir el local", checked: employee ? employee.canOpen : true },
        { label: "Puede cerrar el local", checked: employee ? employee.canClose : true }
    ]);
    form.appendChild(capabilities.fieldset);
    const [splitShiftCheckbox, openCheckbox, closeCheckbox] = capabilities.checkboxes;

    const minEntryInput = document.createElement("input");
    minEntryInput.className = "input";
    minEntryInput.type = "time";
    minEntryInput.value = employee ? toTimeInputValue(employee.minEntryTime) : "";
    form.appendChild(buildField("Hora mínima de entrada", minEntryInput));

    const maxExitInput = document.createElement("input");
    maxExitInput.className = "input";
    maxExitInput.type = "time";
    maxExitInput.value = employee ? toTimeInputValue(employee.maxExitTime) : "";
    form.appendChild(buildField("Hora máxima de salida", maxExitInput));

    const notesInput = document.createElement("textarea");
    notesInput.className = "textarea";
    notesInput.value = employee && employee.internalNotes ? employee.internalNotes : "";
    form.appendChild(buildField("Notas internas", notesInput, "Solo las ve el encargado"));

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
    saveButton.textContent = submitLabel;
    actions.appendChild(saveButton);
    form.appendChild(actions);

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const body = {
            name: nameInput.value,
            email: emailInput.value,
            contractType: contractSelect.value,
            contractHours: contractSelect.value === "PART_TIME" ? Number(hoursInput.value) : null,
            venueId: currentVenueId,
            positions: positions.checkboxes.filter((c) => c.checked).map((c) => c.value),
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

async function loadEmployees() {
    renderEmployees(await fetchJson("/api/employees"));
}

function contractLine(employee) {
    if (employee.contractType !== "PART_TIME") {
        return "Jornada completa";
    }
    return employee.contractHours ? `Parcial · ${employee.contractHours}h/sem` : "Parcial";
}

function positionsLine(employee) {
    if (!employee.positions || employee.positions.length === 0) {
        return "";
    }
    return employee.positions.map((value) => POSITION_LABELS[value] || value).join(", ");
}

/**
 * Una fila por persona. El nombre y las notas los escribe el encargado, así que todo texto
 * ajeno se inserta con textContent y nunca como HTML.
 */
function buildEmployeeRow(employee) {
    const row = document.createElement("tr");

    const nameCell = document.createElement("td");
    const name = document.createElement("div");
    name.className = "cell-title";
    name.textContent = employee.name;
    nameCell.appendChild(name);
    const email = document.createElement("div");
    email.className = "cell-sub";
    email.textContent = employee.email;
    nameCell.appendChild(email);
    row.appendChild(nameCell);

    const contractCell = document.createElement("td");
    contractCell.textContent = contractLine(employee);
    row.appendChild(contractCell);

    const positionsCell = document.createElement("td");
    const positions = positionsLine(employee);
    positionsCell.textContent = positions || "Sin puesto asignado";
    if (!positions) {
        positionsCell.className = "cell-empty";
    }
    row.appendChild(positionsCell);

    const actionsCell = document.createElement("td");
    actionsCell.className = "right";
    const actions = document.createElement("div");
    actions.className = "row-actions";

    if (!employee.active) {
        const inactive = document.createElement("span");
        inactive.className = "pill pill--off";
        const mark = document.createElement("span");
        mark.className = "mark mark--ring";
        inactive.appendChild(mark);
        inactive.appendChild(document.createTextNode("De baja"));
        actions.appendChild(inactive);
    }

    const editButton = document.createElement("button");
    editButton.type = "button";
    editButton.className = "btn btn--secondary btn--sm";
    editButton.textContent = "Editar";
    editButton.addEventListener("click", () => openEmployeeForm(employee));
    actions.appendChild(editButton);

    if (employee.active) {
        const deactivateButton = document.createElement("button");
        deactivateButton.type = "button";
        deactivateButton.className = "btn btn--quiet btn--sm";
        deactivateButton.textContent = "Dar de baja";
        deactivateButton.addEventListener("click", () => {
            confirmAction(
                "Dar de baja",
                `${employee.name} dejará de aparecer en los cuadrantes nuevos. Los ya generados no cambian.`,
                "Dar de baja",
                async () => {
                    try {
                        await fetchJson(`/api/employees/${employee.id}`, { method: "DELETE" });
                        setStatusMessage(`${employee.name} está de baja.`, false);
                        await loadEmployees();
                    } catch (error) {
                        setStatusMessage(error.message, true);
                    }
                });
        });
        actions.appendChild(deactivateButton);
    }

    actionsCell.appendChild(actions);
    row.appendChild(actionsCell);
    return row;
}

function renderEmployees(employees) {
    const list = document.getElementById("employees-list");
    const empty = document.getElementById("employees-empty");
    const table = document.getElementById("employees-table");
    list.replaceChildren();

    const isEmpty = employees.length === 0;
    empty.hidden = !isEmpty;
    table.hidden = isEmpty;
    if (isEmpty) {
        return;
    }
    employees.forEach((employee) => list.appendChild(buildEmployeeRow(employee)));
}

/** Abre la ficha en un modal. Sin empleado, es un alta. */
function openEmployeeForm(employee) {
    const form = buildEmployeeForm(employee, employee ? "Guardar" : "Añadir empleado", async (body) => {
        const url = employee ? `/api/employees/${employee.id}` : "/api/employees";
        try {
            await fetchJson(url, {
                method: employee ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            closeModal();
            setStatusMessage(employee ? `${body.name} actualizado.` : `${body.name} añadido al equipo.`, false);
            await loadEmployees();
        } catch (error) {
            setStatusMessage(error.message, true);
        }
    });
    openModal(employee ? "Editar empleado" : "Añadir empleado", form);
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("new-employee-button").addEventListener("click", () => openEmployeeForm(null));

    fetchJson("/api/auth/me").then((me) => {
        if (me.role !== "MANAGER") {
            window.location.href = "employee.html";
            return;
        }
        currentVenueId = me.venueId;
        return loadEmployees();
    }).catch((error) => setStatusMessage(error.message, true));
});
