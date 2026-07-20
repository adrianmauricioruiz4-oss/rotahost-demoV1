/** Sidebar compartido por todas las vistas. Se inyecta en <aside id="shell-sidebar">. */
(function () {
    "use strict";

    const NAV_ITEMS = [
        {
            href: "index.html",
            label: "Cuadrante",
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4M8 13h2M14 13h2M8 17h2M14 17h2"/></svg>'
        },
        {
            href: "employee.html",
            label: "Mi semana",
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><path d="M4 6h11M4 12h7M4 18h13"/><circle cx="18" cy="6" r="2.3"/><circle cx="14" cy="12" r="2.3"/><circle cx="20" cy="18" r="2.3"/></svg>'
        },
        {
            href: "settings.html",
            label: "Configuración",
            icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/></svg>'
        }
    ];

    function currentPage() {
        const path = window.location.pathname.split("/").pop();
        return path === "" ? "index.html" : path;
    }

    function buildNavItem(item, active) {
        const link = document.createElement("a");
        link.className = "nav-item" + (active ? " active" : "");
        link.href = item.href;

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
            '<div><span class="brandmark">Turnos</span><small id="shell-venue-name">Selecciona un venue</small></div>';
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
        sideCard.innerHTML = "Estás en modo <b>encargado</b>. Nada se publica sin que tú lo revises.";
        target.appendChild(sideCard);
    }

    /** Las vistas llaman a esto cuando conocen el nombre real del venue. */
    window.updateShellVenueName = function updateShellVenueName(name) {
        const el = document.getElementById("shell-venue-name");
        if (el) {
            el.textContent = name;
        }
    };

    document.addEventListener("DOMContentLoaded", () => {
        const target = document.getElementById("shell-sidebar");
        if (target) {
            render(target);
        }
    });
})();
