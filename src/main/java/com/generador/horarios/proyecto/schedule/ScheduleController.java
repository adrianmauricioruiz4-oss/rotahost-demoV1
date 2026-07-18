package com.generador.horarios.proyecto.schedule;

import com.generador.horarios.proyecto.schedule.dto.AssignmentEditResponse;
import com.generador.horarios.proyecto.schedule.dto.EditAssignmentRequest;
import com.generador.horarios.proyecto.schedule.dto.GenerateScheduleRequest;
import com.generador.horarios.proyecto.schedule.dto.ScheduleGenerationResponse;
import com.generador.horarios.proyecto.schedule.dto.SchedulePublishResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleGenerationResponse generate(@Valid @RequestBody GenerateScheduleRequest request) {
        return scheduleService.generate(request);
    }

    @PutMapping("/{scheduleId}/assignments")
    public AssignmentEditResponse editAssignment(
            @PathVariable Long scheduleId, @Valid @RequestBody EditAssignmentRequest request) {
        return scheduleService.editAssignment(scheduleId, request);
    }

    @PostMapping("/{scheduleId}/publish")
    public SchedulePublishResponse publish(@PathVariable Long scheduleId) {
        return scheduleService.publish(scheduleId);
    }
}
