package com.generador.horarios.proyecto.schedule;

import com.generador.horarios.proyecto.schedule.engine.ScheduleGenerator;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cablea ScheduleGenerator/ScheduleValidator como beans para poder
 * inyectarlos en ScheduleService, sin que el paquete engine dependa de
 * Spring (esas clases son Java puro, se instancian aquí con "new").
 */
@Configuration
public class EngineConfig {

    @Bean
    public ScheduleValidator scheduleValidator() {
        return new ScheduleValidator();
    }

    @Bean
    public ScheduleGenerator scheduleGenerator(ScheduleValidator scheduleValidator) {
        return new ScheduleGenerator(scheduleValidator);
    }
}
