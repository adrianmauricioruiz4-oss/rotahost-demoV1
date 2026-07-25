package com.generador.horarios.proyecto.schedule;

import com.generador.horarios.proyecto.schedule.dto.AssignmentEditResponse;
import com.generador.horarios.proyecto.schedule.dto.EditAssignmentRequest;
import com.generador.horarios.proyecto.schedule.dto.GenerateScheduleRequest;
import com.generador.horarios.proyecto.schedule.dto.ScheduleGenerationResponse;
import com.generador.horarios.proyecto.schedule.dto.SchedulePublishResponse;
import com.generador.horarios.proyecto.schedule.dto.ScheduleResponse;
import com.generador.horarios.proyecto.shared.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cada cuenta pertenece a un único venue: toda operación queda restringida al propio (T4.2). */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUserService currentUserService;

    public ScheduleController(ScheduleService scheduleService, CurrentUserService currentUserService) {
        this.scheduleService = scheduleService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ScheduleResponse findByVenueAndWeek(
            @RequestParam Long venueId, @RequestParam int isoYear, @RequestParam int isoWeek, Authentication authentication) {
        currentUserService.requireOwnVenue(venueId, authentication);
        return scheduleService.findByVenueAndWeek(venueId, isoYear, isoWeek);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleGenerationResponse generate(@Valid @RequestBody GenerateScheduleRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(request.venueId(), authentication);
        return scheduleService.generate(request);
    }

    @PutMapping("/{scheduleId}/assignments")
    public AssignmentEditResponse editAssignment(
            @PathVariable Long scheduleId, @Valid @RequestBody EditAssignmentRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(scheduleService.resolveVenueId(scheduleId), authentication);
        return scheduleService.editAssignment(scheduleId, request);
    }

    @PostMapping("/{scheduleId}/publish")
    public SchedulePublishResponse publish(@PathVariable Long scheduleId, Authentication authentication) {
        currentUserService.requireOwnVenue(scheduleService.resolveVenueId(scheduleId), authentication);
        return scheduleService.publish(scheduleId);
    }
}
