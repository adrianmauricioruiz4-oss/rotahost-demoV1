package com.generador.horarios.proyecto.venue.dto;

import java.time.LocalTime;

public record VenueResponse(Long id, String name, LocalTime openingTime, LocalTime closingTime) {
}
