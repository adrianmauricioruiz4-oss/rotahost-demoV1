-- Fichaje (entrada/salida) para empleados e invitados. Independiente del cuadrante: solo
-- registra el hecho de fichar.

create table time_clock_entries (id bigint not null auto_increment, employee_id bigint not null, occurred_at datetime(6) not null, type enum ('CLOCK_IN','CLOCK_OUT') not null, primary key (id)) engine=InnoDB;

alter table time_clock_entries add constraint FKtcemployid foreign key (employee_id) references employees (id);
