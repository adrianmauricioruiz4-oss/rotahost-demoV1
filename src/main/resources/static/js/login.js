/** Login nativo (form POST a /login, lo procesa Spring Security) + mensajes de ?error / ?logout. */
function csrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

function setStatusMessage(message, isError) {
    const el = document.getElementById("status-message");
    if (!message) {
        el.hidden = true;
        return;
    }
    el.textContent = message;
    el.className = "notice " + (isError ? "notice--alert" : "notice--ok");
    el.hidden = false;
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
        let message = `Error ${response.status}`;
        try {
            const body = await response.json();
            if (body && body.message) message = body.message;
        } catch (ignored) {
            // sin cuerpo JSON, nos quedamos con el mensaje genérico
        }
        throw new Error(message);
    }
    if (response.status === 204) return null;
    return response.json();
}

async function loadGuestRoster() {
    const select = document.getElementById("guest-select");
    try {
        const roster = await fetchJson("/api/auth/guest-roster");
        roster.forEach((entry) => {
            const option = document.createElement("option");
            option.value = String(entry.id);
            option.textContent = entry.name;
            select.appendChild(option);
        });
        if (roster.length === 0) {
            select.disabled = true;
            document.getElementById("guest-submit").disabled = true;
        }
    } catch (error) {
        select.disabled = true;
        document.getElementById("guest-submit").disabled = true;
    }
}

async function submitGuestLogin() {
    const select = document.getElementById("guest-select");
    if (!select.value) {
        setStatusMessage("Elige tu nombre para entrar como invitado.", true);
        return;
    }
    try {
        await fetchJson("/api/auth/guest-login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ employeeId: Number(select.value) })
        });
        window.location.href = "/employee.html";
    } catch (error) {
        setStatusMessage("No se ha podido entrar como invitado. Prueba de nuevo.", true);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("csrf-input").value = csrfToken() || "";

    const params = new URLSearchParams(window.location.search);
    if (params.has("error")) {
        setStatusMessage("Correo o contraseña incorrectos.", true);
    } else if (params.has("logout")) {
        setStatusMessage("Sesión cerrada.", false);
    }

    document.getElementById("forgot-password-link").addEventListener("click", (event) => {
        event.preventDefault();
        showToast("La recuperación de contraseña todavía no está disponible. Pídesela a tu encargado.", "warn");
    });

    loadGuestRoster();
    document.getElementById("guest-submit").addEventListener("click", submitGuestLogin);
});
