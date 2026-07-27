/**
 * Menú lateral compartido por todas las vistas. Cada página trae el hueco
 * <div class="sidebar" id="shell-nav"> y esto lo rellena: marca arriba, secciones con su
 * icono, y la configuración y el cerrar sesión abajo del todo.
 *
 * Los iconos son de navegación, no decoración: acompañan siempre a su texto, salvo la rueda
 * de configuración, que va sola con su aria-label porque es un icono universal.
 */
(function () {
    "use strict";

    const ICONS = {
        panel: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>',
        rota: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/></svg>',
        people: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
        clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
        week: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13M8 12h13M8 18h13"/><path d="M3 6h.01M3 12h.01M3 18h.01"/></svg>',
        gear: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
        exit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5M21 12H9"/></svg>'
    };

    /**
     * Fichar y fichajes viven en la misma sección: son lo mismo visto desde los dos lados,
     * y la pantalla enseña una cosa u otra según quién entre.
     */
    const NAV_ITEMS = [
        { href: "dashboard.html", label: "Panel", icon: "panel", managerOnly: true },
        { href: "index.html", label: "Cuadrante", icon: "rota", managerOnly: true },
        { href: "employees.html", label: "Empleados", icon: "people", managerOnly: true },
        { href: "fichajes.html", label: "Fichajes", icon: "clock", managerOnly: false },
        { href: "employee.html", label: "Mi semana", icon: "week", managerOnly: false }
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

    function iconElement(name) {
        const wrapper = document.createElement("span");
        wrapper.innerHTML = ICONS[name];
        return wrapper.firstElementChild;
    }

    function render(target) {
        const active = currentPage();
        target.replaceChildren();

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
            link.className = "nav-item";
            link.href = item.href;
            link.dataset.managerOnly = String(item.managerOnly);
            link.appendChild(iconElement(item.icon));
            const label = document.createElement("span");
            label.textContent = item.label;
            link.appendChild(label);
            if (item.href === active) {
                link.setAttribute("aria-current", "page");
            }
            nav.appendChild(link);
        });
        target.appendChild(nav);

        const spacer = document.createElement("div");
        spacer.className = "nav-spacer";
        target.appendChild(spacer);

        const foot = document.createElement("div");
        foot.className = "sidebar-foot";

        const exit = document.createElement("button");
        exit.type = "button";
        exit.className = "nav-item";
        exit.appendChild(iconElement("exit"));
        const exitLabel = document.createElement("span");
        exitLabel.textContent = "Cerrar sesión";
        exit.appendChild(exitLabel);
        exit.addEventListener("click", logout);
        foot.appendChild(exit);

        // La rueda va sola, sin texto: es el icono que todo el mundo reconoce.
        const settings = document.createElement("a");
        settings.className = "icon-btn";
        settings.href = "settings.html";
        settings.dataset.managerOnly = "true";
        settings.setAttribute("aria-label", "Configuración");
        settings.title = "Configuración";
        settings.appendChild(iconElement("gear"));
        if (active === "settings.html") {
            settings.setAttribute("aria-current", "page");
        }
        foot.appendChild(settings);

        target.appendChild(foot);
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
     * su cuenta (panel y cuadrante): si no, el menú se queda en el texto de relleno.
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
        const nav = document.getElementById("shell-nav");
        if (nav) {
            render(nav);
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
