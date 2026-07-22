-- Capacidades del empleado (T5.2). Los tres booleanos parten en true (mismo default que la
-- entidad) para no excluir de golpe a los empleados ya existentes de nada que el generador
-- pueda llegar a filtrar por esto en T5.3.

alter table employees add column can_work_split_shift bit not null default 1;
alter table employees add column can_open bit not null default 1;
alter table employees add column can_close bit not null default 1;
alter table employees add column min_entry_time time(0) null;
alter table employees add column max_exit_time time(0) null;
alter table employees add column internal_notes varchar(2000) null;
