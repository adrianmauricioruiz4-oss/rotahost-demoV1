-- Corrección de fichajes por el encargado. El registro original NO se borra ni se sustituye:
-- se conserva su hora en original_occurred_at y queda constancia de quién lo cambió, cuándo y
-- por qué. El registro horario es obligatorio y hay que poder justificar cada retoque ante una
-- inspección, así que aquí no hay borrado, solo rastro.

alter table time_clock_entries add column original_occurred_at datetime(6) null;
alter table time_clock_entries add column corrected_at datetime(6) null;
alter table time_clock_entries add column corrected_by_id bigint null;
alter table time_clock_entries add column correction_reason varchar(500) null;

-- Marca los fichajes que no puso el propio empleado, sino el encargado al cerrar una jornada
-- que quedó abierta. Se distingue del resto a propósito: no es lo mismo lo que uno ficha que
-- lo que otro anota por él.
alter table time_clock_entries add column added_by_manager bit not null default 0;

alter table time_clock_entries add constraint FKtccorrectedby foreign key (corrected_by_id) references employees (id);
