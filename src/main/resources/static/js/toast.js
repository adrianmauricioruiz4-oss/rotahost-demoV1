/** Notificaciones flotantes compartidas por todas las vistas. Uso: showToast("mensaje"[, "warn"]). */
(function () {
    "use strict";

    const CHECK_ICON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>';
    const WARN_ICON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 9v4M12 17h.01M10.3 3.9 2 18a2 2 0 0 0 1.7 3h16.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>';

    function ensureContainer() {
        let container = document.getElementById("toasts");
        if (!container) {
            container = document.createElement("div");
            container.id = "toasts";
            container.className = "toast-stack";
            document.body.appendChild(container);
        }
        return container;
    }

    function iconElement(type) {
        const wrapper = document.createElement("span");
        wrapper.innerHTML = type === "warn" ? WARN_ICON : CHECK_ICON;
        return wrapper.firstElementChild;
    }

    window.showToast = function showToast(message, type) {
        const container = ensureContainer();
        const toast = document.createElement("div");
        toast.className = "toast" + (type === "warn" ? " warn" : "");
        toast.setAttribute("role", "status");
        toast.appendChild(iconElement(type));

        const text = document.createElement("span");
        text.textContent = message;
        toast.appendChild(text);

        container.appendChild(toast);
        setTimeout(() => {
            toast.style.transition = "opacity .3s, transform .3s";
            toast.style.opacity = "0";
            toast.style.transform = "translateY(8px)";
            setTimeout(() => toast.remove(), 300);
        }, 2600);
    };
})();
