/**
 * Avisos en línea del sistema de diseño. Sustituyen a los toast flotantes de antes,
 * que el sistema no contempla: DESIGN.md prohíbe iconos decorativos y animaciones
 * fuera de las transiciones de .btn, y pide que los avisos dinámicos lleven role="status".
 *
 * Uso: showNotice("id-del-hueco", "Sesión cerrada", "ok"). Sin mensaje, se oculta.
 * Tonos: "alert", "warn", "ok", "info". La forma de la marca sale de components.html,
 * porque el estado nunca se comunica solo con color.
 */
(function () {
    "use strict";

    const MARKS = {
        alert: "mark--dot",
        warn: "mark--bars",
        ok: "mark--dot",
        info: "mark--ring"
    };

    window.showNotice = function showNotice(target, message, kind) {
        const el = typeof target === "string" ? document.getElementById(target) : target;
        if (!el) {
            return;
        }
        el.replaceChildren();
        if (!message) {
            el.hidden = true;
            return;
        }

        const tone = MARKS[kind] ? kind : "info";
        el.className = "notice notice--" + tone;

        const mark = document.createElement("span");
        mark.className = "mark " + MARKS[tone];
        el.appendChild(mark);

        const text = document.createElement("span");
        text.textContent = message;
        el.appendChild(text);

        el.hidden = false;
    };
})();
