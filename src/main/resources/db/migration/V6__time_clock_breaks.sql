-- Pausas dentro de la jornada. Se registran como dos marcas propias (BREAK_START/BREAK_END)
-- en lugar de como una salida y una entrada, para poder distinguirlas al contar la jornada.

alter table time_clock_entries modify column type enum ('CLOCK_IN','BREAK_START','BREAK_END','CLOCK_OUT') not null;

-- Minutos de pausa que el local reconoce como tiempo trabajado dentro de una jornada. Lo que
-- se pase de aquí sí se descuenta del cómputo. Se edita desde la pantalla de configuración:
-- cada local tiene su convenio y su acuerdo, y este número no se decide en el código.
alter table venues add column break_allowance_minutes int not null default 15;
