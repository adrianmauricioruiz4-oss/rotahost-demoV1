/** Sidebar compartido por todas las vistas. Se inyecta en <aside id="shell-sidebar">. */
(function () {
    "use strict";

    const NAV_ITEMS = [
        {
            href: "dashboard.html",
            label: "Panel",
            managerOnly: true,
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>'
        },
        {
            href: "index.html",
            label: "Cuadrante",
            managerOnly: true,
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4M8 13h2M14 13h2M8 17h2M14 17h2"/></svg>'
        },
        {
            href: "employees.html",
            label: "Empleados",
            managerOnly: true,
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>'
        },
        {
            href: "employee.html",
            label: "Mi semana",
            managerOnly: false,
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><path d="M4 6h11M4 12h7M4 18h13"/><circle cx="18" cy="6" r="2.3"/><circle cx="14" cy="12" r="2.3"/><circle cx="20" cy="18" r="2.3"/></svg>'
        },
        {
            href: "settings.html",
            label: "Configuración",
            managerOnly: true,
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/></svg>'
        }
    ];

    function csrfToken() {
        const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    function currentPage() {
        const path = window.location.pathname.split("/").pop();
        return path === "" ? "index.html" : path;
    }

    function buildNavItem(item, active) {
        const link = document.createElement("a");
        link.className = "nav-item" + (active ? " active" : "");
        link.href = item.href;
        link.dataset.managerOnly = String(item.managerOnly);

        const icon = document.createElement("span");
        icon.innerHTML = item.icon;
        link.appendChild(icon.firstElementChild);

        const label = document.createElement("span");
        label.textContent = item.label;
        link.appendChild(label);

        return link;
    }

    function render(target) {
        const active = currentPage();

        target.innerHTML = "";

        const brand = document.createElement("div");
        brand.className = "brand";
        brand.innerHTML =
            '<div class="logo"><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M8 2v3M16 2v3M3 9h18"/><rect x="3" y="5" width="18" height="17" rx="3"/></svg></div>' +
            '<div><span class="brandmark">Turnos</span><small id="shell-venue-name">Cargando…</small></div>';
        target.appendChild(brand);

        const nav = document.createElement("nav");
        nav.className = "nav";
        NAV_ITEMS.forEach((item) => nav.appendChild(buildNavItem(item, item.href === active)));
        target.appendChild(nav);

        const spacer = document.createElement("div");
        spacer.className = "nav-spacer";
        target.appendChild(spacer);

        const sideCard = document.createElement("div");
        sideCard.className = "side-card";
        sideCard.id = "shell-side-card";
        sideCard.innerHTML = "Estás en modo <b>encargado</b>. Nada se publica sin que tú lo revises.";
        target.appendChild(sideCard);

        const logoutLink = document.createElement("a");
        logoutLink.href = "#";
        logoutLink.className = "nav-item";
        logoutLink.textContent = "Cerrar sesión";
        logoutLink.addEventListener("click", (event) => {
            event.preventDefault();
            fetch("/logout", { method: "POST", headers: csrfToken() ? { "X-XSRF-TOKEN": csrfToken() } : {} })
                .finally(() => { window.location.href = "/"; });
        });
        target.appendChild(logoutLink);
    }

    /** Las vistas llaman a esto cuando conocen el nombre real del venue. */
    window.updateShellVenueName = function updateShellVenueName(name) {
        const el = document.getElementById("shell-venue-name");
        if (el) {
            el.textContent = name;
        }
    };

    /**
     * Pinta el saludo y recorta la navegación según el rol.
     * El nombre se inserta con textContent, nunca con innerHTML: lo escribe el encargado
     * al dar de alta al empleado, así que es texto ajeno y no debe interpretarse como HTML.
     */
    function applyCurrentUser(me) {
        const sideCard = document.getElementById("shell-side-card");
        if (sideCard) {
            const isManager = me.role === "MANAGER";
            const tail = me.guest
                ? ". Modo invitado: puedes mirar, no tocar."
                : (isManager
                    ? ". Modo encargado: nada se publica sin que tú lo revises."
                    : ". Modo empleado.");

            sideCard.textContent = "Hola, ";
            const name = document.createElement("b");
            name.textContent = me.name;
            sideCard.appendChild(name);
            sideCard.appendChild(document.createTextNode(tail));
        }
        if (me.role !== "MANAGER") {
            document.querySelectorAll('.nav-item[data-manager-only="true"]').forEach((link) => link.remove());
        }
    }

    /**
     * Resuelve el nombre del local a partir del venueId de la sesión. Lo hace el shell
     * para que todas las vistas lo muestren, no solo las que ya cargaban el venue por
     * su cuenta (panel y cuadrante): si no, el sidebar se queda en el texto de relleno.
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
        const target = document.getElementById("shell-sidebar");
        if (target) {
            render(target);
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
