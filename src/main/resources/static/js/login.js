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
    el.className = "status-message " + (isError ? "error" : "success");
    el.hidden = false;
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
});
