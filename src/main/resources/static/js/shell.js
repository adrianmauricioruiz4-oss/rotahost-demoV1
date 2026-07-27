/** Topbar compartida por todas las vistas de encargado/empleado. Se inyecta en <header id="shell-topbar">. */
(function () {
    "use strict";

    const NAV_ITEMS = [
        { href: "index.html", label: "Cuadrante", managerOnly: true },
        { href: "employees.html", label: "Empleados", managerOnly: true },
        { href: "employee.html", label: "Mi semana", managerOnly: false },
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

    function buildNavItem(item, active) {
        const link = document.createElement("a");
        link.href = item.href;
        link.textContent = item.label;
        link.dataset.managerOnly = String(item.managerOnly);
        if (active) {
            link.setAttribute("aria-current", "page");
        }
        return link;
    }

    function render(target) {
        const active = currentPage();

        target.innerHTML = "";
        target.className = "topbar";

        const brand = document.createElement("span");
        brand.className = "brand";
        brand.textContent = "Turnos";
        target.appendChild(brand);

        const venueName = document.createElement("span");
        venueName.className = "text-caption";
        venueName.id = "shell-venue-name";
        venueName.textContent = "Selecciona un venue";
        target.appendChild(venueName);

        const nav = document.createElement("nav");
        NAV_ITEMS.forEach((item) => nav.appendChild(buildNavItem(item, item.href === active)));
        target.appendChild(nav);

        const logoutLink = document.createElement("a");
        logoutLink.href = "#";
        logoutLink.className = "logout-link";
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

    function applyCurrentUser(me) {
        if (me.role !== "MANAGER") {
            document.querySelectorAll('#shell-topbar nav a[data-manager-only="true"]').forEach((link) => link.remove());
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        const target = document.getElementById("shell-topbar");
        if (target) {
            render(target);
        }
        fetch("/api/auth/me")
            .then((response) => (response.ok ? response.json() : null))
            .then((me) => { if (me) applyCurrentUser(me); })
            .catch(() => {});
    });
})();
