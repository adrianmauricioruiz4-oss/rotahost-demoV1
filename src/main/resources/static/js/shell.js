/**
 * Barra de navegación compartida por todas las vistas. Cada página trae el hueco
 * <div class="topbar" id="shell-topbar"> y esto lo rellena.
 */
(function () {
    "use strict";

    const NAV_ITEMS = [
        { href: "dashboard.html", label: "Panel", managerOnly: true },
        { href: "index.html", label: "Cuadrante", managerOnly: true },
        { href: "employees.html", label: "Empleados", managerOnly: true },
        { href: "fichajes.html", label: "Fichajes", managerOnly: true },
        { href: "employee.html", label: "Mi semana", managerOnly: false },
        { href: "fichar.html", label: "Fichar", managerOnly: false },
        { href: "settings.html", label: "Configuración", managerOnly: true }
    ];

    function csrfToken() {
        const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    function currentPage() {
        const path = window.location.pathname.split("/").pop();
        return path === "" ? "index.html" : path;
    }

    function logout() {
        fetch("/logout", { method: "POST", headers: csrfToken() ? { "X-XSRF-TOKEN": csrfToken() } : {} })
            .finally(() => { window.location.href = "/"; });
    }

    function renderTopbar(target) {
        const active = currentPage();
        target.innerHTML = "";

        const brand = document.createElement("span");
        brand.className = "brand";
        brand.textContent = "RotaTeam";
        target.appendChild(brand);

        const venue = document.createElement("span");
        venue.className = "venue-name";
        venue.id = "shell-venue-name";
        venue.textContent = "Cargando…";
        target.appendChild(venue);

        const nav = document.createElement("nav");
        NAV_ITEMS.forEach((item) => {
            const link = document.createElement("a");
            link.href = item.href;
            link.textContent = item.label;
            link.dataset.managerOnly = String(item.managerOnly);
            if (item.href === active) {
                link.setAttribute("aria-current", "page");
            }
            nav.appendChild(link);
        });
        target.appendChild(nav);

        const exit = document.createElement("button");
        exit.type = "button";
        exit.className = "btn btn--quiet btn--sm";
        exit.textContent = "Cerrar sesión";
        exit.addEventListener("click", logout);
        target.appendChild(exit);
    }

    /** Las vistas llaman a esto cuando conocen el nombre real del venue. */
    window.updateShellVenueName = function updateShellVenueName(name) {
        const el = document.getElementById("shell-venue-name");
        if (el) {
            el.textContent = name;
        }
    };

    /** Recorta la navegación según el rol: quien no es encargado no ve sus secciones. */
    function applyCurrentUser(me) {
        if (me.role !== "MANAGER") {
            document.querySelectorAll('[data-manager-only="true"]').forEach((link) => link.remove());
        }
    }

    /**
     * Resuelve el nombre del local a partir del venueId de la sesión. Lo hace el shell
     * para que todas las vistas lo muestren, no solo las que ya cargaban el venue por
     * su cuenta (panel y cuadrante): si no, la barra se queda en el texto de relleno.
     */
    function loadVenueName(me) {
        if (!me.venueId) {
            return;
        }
        fetch(`/api/venues/${me.venueId}`)
            .then((response) => (response.ok ? response.json() : null))
            .then((venue) => { if (venue && venue.name) window.updateShellVenueName(venue.name); })
            .catch(() => {});
    }

    document.addEventListener("DOMContentLoaded", () => {
        const topbar = document.getElementById("shell-topbar");
        if (topbar) {
            renderTopbar(topbar);
        }
        fetch("/api/auth/me")
            .then((response) => (response.ok ? response.json() : null))
            .then((me) => {
                if (!me) return;
                applyCurrentUser(me);
                loadVenueName(me);
            })
            .catch(() => {});
    });
})();
