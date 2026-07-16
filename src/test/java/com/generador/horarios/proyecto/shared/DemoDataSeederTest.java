package com.generador.horarios.proyecto.shared;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private ShiftTemplateRepository shiftTemplateRepository;

    @Mock
    private CoverageRequirementRepository coverageRequirementRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Captor
    private ArgumentCaptor<List<CoverageRequirement>> coverageCaptor;

    @Captor
    private ArgumentCaptor<List<Employee>> employeeCaptor;

    @Test
    void seedsVenueShiftTemplatesCoverageAndTenEmployeesWhenEmpty() {
        DemoDataSeeder seeder = new DemoDataSeeder(
                venueRepository, shiftTemplateRepository, coverageRequirementRepository, employeeRepository);

        when(venueRepository.count()).thenReturn(0L);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run();

        verify(venueRepository).save(any(Venue.class));
        verify(shiftTemplateRepository, times(3)).save(any(ShiftTemplate.class));

        verify(coverageRequirementRepository).saveAll(coverageCaptor.capture());
        Assertions.assertThat(coverageCaptor.getValue()).hasSize(21);

        verify(employeeRepository).saveAll(employeeCaptor.capture());
        Assertions.assertThat(employeeCaptor.getValue()).hasSize(10);
    }

    @Test
    void doesNothingWhenVenueAlreadyExists() {
        DemoDataSeeder seeder = new DemoDataSeeder(
                venueRepository, shiftTemplateRepository, coverageRequirementRepository, employeeRepository);

        when(venueRepository.count()).thenReturn(1L);

        seeder.run();

        verify(venueRepository, never()).save(any());
        verify(shiftTemplateRepository, never()).save(any());
        verify(coverageRequirementRepository, never()).saveAll(anyList());
        verify(employeeRepository, never()).saveAll(anyList());
    }
}
