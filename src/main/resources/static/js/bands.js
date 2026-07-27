/**
 * Franjas del día, compartidas por el cuadrante y el panel principal.
 *
 * Sirven para dos cosas distintas que conviene no mezclar:
 *  - el COLOR de una celda, que sale de la hora de entrada, porque es como se habla de los
 *    turnos en un local ("el de las ocho" es el de mañana aunque acabe a las cuatro);
 *  - el RECUENTO de gente por tramo, que sale del solape real, porque para saber cuántos
 *    hay en la barra a las dos de la tarde da igual cómo se llame el turno.
 */
const BANDS = [
    { id: "morning", label: "Mañana", from: 6 * 60, to: 12 * 60 },
    { id: "midday", label: "Mediodía", from: 12 * 60, to: 17 * 60 },
    { id: "evening", label: "Tarde y noche", from: 17 * 60, to: 30 * 60 }
];

/** "16:00" -> 960. */
function minutesOfDay(localTime) {
    const [hours, minutes] = localTime.slice(0, 5).split(":").map(Number);
    return hours * 60 + minutes;
}

/** Un tramo que cruza la medianoche termina "después de las 24h", no a las 00:00. */
function segmentRange(segment) {
    const start = minutesOfDay(segment.startTime);
    let end = minutesOfDay(segment.endTime);
    if (end <= start) {
        end += 24 * 60;
    }
    return { start, end };
}

/** Franjas que toca un turno. Un partido toca dos, y por eso cuenta en ambas. */
function bandsOfShift(shiftTemplate) {
    const touched = new Set();
    shiftTemplate.segments.forEach((segment) => {
        const { start, end } = segmentRange(segment);
        BANDS.forEach((band) => {
            if (start < band.to && end > band.from) {
                touched.add(band.id);
            }
        });
    });
    return touched;
}

/**
 * El color de la celda. Los turnos de varios tramos —el partido— llevan el suyo propio: no
 * son ni una cosa ni otra, y de un vistazo lo que interesa es justamente que están partidos.
 */
function shiftColourOf(shiftTemplate) {
    if (shiftTemplate.segments.length > 1) {
        return "night";
    }
    const start = minutesOfDay(shiftTemplate.segments[0].startTime);
    if (start < 12 * 60) {
        return "morning";
    }
    return start < 15 * 60 ? "midday" : "evening";
}

/**
 * Pinta la tabla de "cuánta gente hay por franja": una fila por franja, una columna por día.
 *
 * @param headRow          <tr> de la cabecera
 * @param body             <tbody> donde van las filas
 * @param days             [{date, label}] de la semana
 * @param employeeIds      ids de las personas a contar
 * @param assignmentsByEmployeeDate Map(employeeId -> Map(fecha -> shiftTemplateId))
 * @param shiftTemplateById función que devuelve el turno a partir de su id
 */
function renderBandTable(headRow, body, days, employeeIds, assignmentsByEmployeeDate, shiftTemplateById) {
    headRow.replaceChildren();
    body.replaceChildren();

    const corner = document.createElement("th");
    corner.scope = "col";
    corner.textContent = "Franja";
    headRow.appendChild(corner);
    days.forEach((day) => {
        const th = document.createElement("th");
        th.scope = "col";
        th.textContent = day.label;
        headRow.appendChild(th);
    });

    BANDS.forEach((band) => {
        const row = document.createElement("tr");

        const name = document.createElement("th");
        name.scope = "row";
        name.textContent = band.label;
        row.appendChild(name);

        days.forEach((day) => {
            let people = 0;
            employeeIds.forEach((employeeId) => {
                const shiftTemplateId = assignmentsByEmployeeDate.get(employeeId)?.get(day.date);
                const shiftTemplate = shiftTemplateId ? shiftTemplateById(shiftTemplateId) : null;
                if (shiftTemplate && bandsOfShift(shiftTemplate).has(band.id)) {
                    people += 1;
                }
            });

            const cell = document.createElement("td");
            const value = document.createElement("span");
            value.className = people > 0 ? `band-count band-count--${band.id}` : "band-count band-count--none";
            value.textContent = String(people);
            cell.appendChild(value);
            row.appendChild(cell);
        });

        body.appendChild(row);
    });
}
