-- Puestos del empleado (T5.1): uno o varios por persona. Generado desde la entidad vía
-- jakarta.persistence.schema-generation.scripts.action=create, igual que V1.

create table employee_positions (employee_id bigint not null, position enum ('AYUDANTE_COCINA','CAMARERO','COCINERO','ENCARGADO','REPARTIDOR','RESPONSABLE_SALA') not null, primary key (employee_id, position)) engine=InnoDB;

alter table employee_positions add constraint FKllmgfchs5nrmadj0ypu9f71wi foreign key (employee_id) references employees (id);
