package com.generador.horarios.proyecto.preference;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {

    List<Preference> findByEmployeeId(Long employeeId);

    List<Preference> findByEmployeeIdIn(Collection<Long> employeeIds);
}
