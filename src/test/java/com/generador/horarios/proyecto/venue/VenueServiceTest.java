package com.generador.horarios.proyecto.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.venue.dto.VenueRequest;
import com.generador.horarios.proyecto.venue.dto.VenueResponse;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    private VenueService venueService;
    private Venue venue;

    @BeforeEach
    void setUp() {
        venueService = new VenueService(venueRepository);
        venue = new Venue("Bar La Esquina", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);
    }

    @Test
    void findsAllVenues() {
        when(venueRepository.findAll()).thenReturn(List.of(venue));

        List<VenueResponse> responses = venueService.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Bar La Esquina");
    }

    @Test
    void findsVenueById() {
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));

        VenueResponse response = venueService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.openingTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.closingTime()).isEqualTo(LocalTime.of(2, 0));
    }

    @Test
    void rejectsUnknownVenueOnFindById() {
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.findById(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updatesNameAndOpeningHours() {
        VenueRequest request = new VenueRequest("Bar La Esquina Renovado", LocalTime.of(9, 0), LocalTime.of(1, 0));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));

        VenueResponse response = venueService.update(1L, request);

        assertThat(response.name()).isEqualTo("Bar La Esquina Renovado");
        assertThat(response.openingTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.closingTime()).isEqualTo(LocalTime.of(1, 0));
    }

    @Test
    void rejectsUnknownVenueOnUpdate() {
        VenueRequest request = new VenueRequest("Nombre", LocalTime.of(9, 0), LocalTime.of(1, 0));
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.update(99L, request))
                .isInstanceOf(ResponseStatusException.class);
    }
}
