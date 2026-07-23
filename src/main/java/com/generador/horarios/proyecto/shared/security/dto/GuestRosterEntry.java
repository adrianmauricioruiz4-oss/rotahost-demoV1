package com.generador.horarios.proyecto.shared.security.dto;

/** Una entrada seleccionable en el picker de "entrar como invitado" (login.html). Sin datos sensibles. */
public record GuestRosterEntry(Long id, String name) {
}
