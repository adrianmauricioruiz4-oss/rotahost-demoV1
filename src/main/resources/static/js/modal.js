/**
 * Ventana emergente compartida. DESIGN.md la reserva para correcciones y confirmaciones
 * destructivas, así que aquí solo se usa para la ficha de empleado, la corrección de un
 * fichaje y los "¿seguro?".
 *
 * Uso:
 *   openModal("Añadir empleado", nodoConElContenido);
 *   closeModal();
 *
 * El contenido trae sus propios botones; para cerrar desde ahí, llamar a closeModal().
 * Se puede cerrar con Escape y pulsando fuera, y el foco vuelve a donde estaba.
 */
(function () {
    "use strict";

    const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    let backdrop = null;
    let previouslyFocused = null;

    function onKeydown(event) {
        if (event.key === "Escape") {
            closeModal();
            return;
        }
        if (event.key !== "Tab" || !backdrop) {
            return;
        }
        // El foco no sale del modal mientras está abierto: la app tiene que ser usable con teclado.
        const focusable = backdrop.querySelectorAll(FOCUSABLE);
        if (focusable.length === 0) {
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    window.openModal = function openModal(title, content) {
        closeModal();
        previouslyFocused = document.activeElement;

        backdrop = document.createElement("div");
        backdrop.className = "modal-backdrop";
        backdrop.addEventListener("mousedown", (event) => {
            if (event.target === backdrop) {
                closeModal();
            }
        });

        const modal = document.createElement("div");
        modal.className = "modal";
        modal.setAttribute("role", "dialog");
        modal.setAttribute("aria-modal", "true");
        modal.setAttribute("aria-labelledby", "modal-title");

        const heading = document.createElement("h3");
        heading.id = "modal-title";
        heading.textContent = title;
        modal.appendChild(heading);
        modal.appendChild(content);

        backdrop.appendChild(modal);
        document.body.appendChild(backdrop);
        document.addEventListener("keydown", onKeydown);

        const first = modal.querySelector(FOCUSABLE);
        (first || modal).focus();
    };

    window.closeModal = function closeModal() {
        if (!backdrop) {
            return;
        }
        document.removeEventListener("keydown", onKeydown);
        backdrop.remove();
        backdrop = null;
        if (previouslyFocused && typeof previouslyFocused.focus === "function") {
            previouslyFocused.focus();
        }
        previouslyFocused = null;
    };

    /**
     * Confirmación de una acción que no se puede deshacer. Sustituye al confirm() del
     * navegador, que no se puede peinar y no encaja con el resto de la interfaz.
     */
    window.confirmAction = function confirmAction(title, question, confirmLabel, onConfirm) {
        const body = document.createElement("div");

        const text = document.createElement("p");
        text.className = "text-secondary";
        text.style.marginTop = "var(--s-3)";
        text.textContent = question;
        body.appendChild(text);

        const actions = document.createElement("div");
        actions.className = "row-end";
        actions.style.marginTop = "var(--s-6)";

        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "btn btn--secondary";
        cancel.textContent = "Cancelar";
        cancel.addEventListener("click", closeModal);
        actions.appendChild(cancel);

        const confirm = document.createElement("button");
        confirm.type = "button";
        confirm.className = "btn btn--danger";
        confirm.textContent = confirmLabel;
        confirm.addEventListener("click", () => {
            closeModal();
            onConfirm();
        });
        actions.appendChild(confirm);

        body.appendChild(actions);
        openModal(title, body);
    };
})();
