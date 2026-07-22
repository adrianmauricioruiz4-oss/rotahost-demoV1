-- Puesto opcional por requisito de cobertura (T5.3): null = cualquier puesto, como antes.
-- Permite varias filas para el mismo día+turno, una por puesto (ej. "2 camareros + 1 encargado").

alter table coverage_requirements add column position enum ('AYUDANTE_COCINA','CAMARERO','COCINERO','ENCARGADO','REPARTIDOR','RESPONSABLE_SALA') null;
