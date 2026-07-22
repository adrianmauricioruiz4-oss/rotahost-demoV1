-- Esquema inicial (T4.3), generado desde las entidades JPA vía
-- jakarta.persistence.schema-generation.scripts.action=create con el dialecto
-- MySQL de Hibernate, para que coincida exactamente con lo que ya validaba
-- ddl-auto=update/create-drop. H2 corre en MODE=MySQL para poder aplicar esta
-- misma migración en dev/test sin mantener un script por motor.

create table coverage_requirements (required_count integer not null, id bigint not null auto_increment, shift_template_id bigint not null, venue_id bigint not null, day_of_week enum ('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') not null, primary key (id)) engine=InnoDB;
create table employees (active bit not null, contract_hours integer, id bigint not null auto_increment, venue_id bigint not null, email varchar(255) not null, name varchar(255) not null, password varchar(255), contract_type enum ('FULL_TIME','PART_TIME') not null, role enum ('EMPLOYEE','MANAGER') not null, primary key (id)) engine=InnoDB;
create table preferences (specific_date date, weight integer not null, employee_id bigint not null, id bigint not null auto_increment, shift_template_id bigint, day_of_week enum ('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY'), type enum ('AVOIDS_DAY','AVOIDS_SHIFT','PREFERS_DAY','PREFERS_SHIFT','UNAVAILABLE') not null, primary key (id)) engine=InnoDB;
create table schedules (iso_week integer not null, iso_year integer not null, id bigint not null auto_increment, venue_id bigint not null, status enum ('DRAFT','PUBLISHED') not null, primary key (id)) engine=InnoDB;
create table shift_assignments (date date not null, employee_id bigint not null, id bigint not null auto_increment, schedule_id bigint not null, shift_template_id bigint not null, primary key (id)) engine=InnoDB;
create table shift_template_segments (end_time time(0) not null, segment_order integer not null, start_time time(0) not null, shift_template_id bigint not null, primary key (segment_order, shift_template_id), check ((segment_order>=0))) engine=InnoDB;
create table shift_templates (active bit not null, id bigint not null auto_increment, venue_id bigint not null, name varchar(255) not null, primary key (id)) engine=InnoDB;
create table venues (closing_time time(0) not null, opening_time time(0) not null, id bigint not null auto_increment, name varchar(255) not null, primary key (id)) engine=InnoDB;

alter table employees add constraint UKj9xgmd0ya5jmus09o0b8pqrpb unique (email);
alter table schedules add constraint UKn7d1rcgpey9ekb9gapo032ksy unique (venue_id, iso_year, iso_week);

alter table coverage_requirements add constraint FKlb1ywxuypwyrahsa9dhxd6r0a foreign key (shift_template_id) references shift_templates (id);
alter table coverage_requirements add constraint FKrknvtup69csxe627rs5dmslyx foreign key (venue_id) references venues (id);
alter table employees add constraint FKqri8ug9wuicrjyy01de1kuuc7 foreign key (venue_id) references venues (id);
alter table preferences add constraint FKko93ap3jegstdmxosbney4con foreign key (employee_id) references employees (id);
alter table preferences add constraint FKqkbx3tx1r4mqs1sby70xjo2vw foreign key (shift_template_id) references shift_templates (id);
alter table schedules add constraint FKs1y7s6x69j1lnnrympg3d8q6r foreign key (venue_id) references venues (id);
alter table shift_assignments add constraint FKbfmt35sngf827e1fldbpvdi8a foreign key (employee_id) references employees (id);
alter table shift_assignments add constraint FK7cw8mcrqd9ltwwi8k7a8gn1dq foreign key (schedule_id) references schedules (id);
alter table shift_assignments add constraint FKinxfl50blxni0147mf9gpi8s8 foreign key (shift_template_id) references shift_templates (id);
alter table shift_template_segments add constraint FK5lfmqvlay0rhlt04b02jtgm1y foreign key (shift_template_id) references shift_templates (id);
alter table shift_templates add constraint FK7vhua1jd7e1n0t68s5g7vrkq1 foreign key (venue_id) references venues (id);
