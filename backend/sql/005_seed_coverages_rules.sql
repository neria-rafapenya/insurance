-- Seed mínimo de coberturas y reglas para validación/pricing.

-- Compatibilidad: asegurar columna precio_extra (si solo existe precioExtra).
SET @has_precio_extra := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ins_coverages'
    AND column_name = 'precio_extra'
);

SET @has_precioExtra := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ins_coverages'
    AND column_name = 'precioExtra'
);

SET @sql := IF(
  @has_precio_extra = 0,
  'ALTER TABLE ins_coverages ADD COLUMN precio_extra VARCHAR(255) NOT NULL DEFAULT \"0\"',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  @has_precioExtra > 0,
  'ALTER TABLE ins_coverages MODIFY COLUMN precioExtra VARCHAR(255) NOT NULL DEFAULT \"0\"',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_precio_extra := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ins_coverages'
    AND column_name = 'precio_extra'
);

SET @sql := IF(
  @has_precioExtra > 0 AND @has_precio_extra > 0,
  'UPDATE ins_coverages SET precio_extra = COALESCE(precio_extra, precioExtra)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Limpiar datos existentes para evitar duplicados.
DELETE FROM ins_coverages WHERE tipo_seguro IN ('auto', 'hogar', 'salud', 'viaje');
DELETE FROM ins_risk_rules WHERE tipo_seguro IN ('auto', 'hogar', 'salud', 'viaje');
DELETE FROM ins_pricing_rules WHERE tipo_seguro IN ('auto', 'hogar', 'salud', 'viaje');

-- Coberturas
INSERT INTO ins_coverages (tipo_seguro, nombre, incluido, precio_extra, descripcion) VALUES
('auto', 'Responsabilidad civil', 1, '0', 'Cubre los daños personales y materiales que causes a terceros con el vehículo. Incluye defensa y reclamación de daños en procedimientos básicos.'),
('auto', 'Asistencia en carretera', 1, '0', 'Asistencia desde el km 0 en caso de avería o accidente. Incluye remolque, ayuda in situ y traslado de ocupantes según condiciones.'),
('auto', 'Defensa jurídica', 1, '0', 'Cobertura de gastos de defensa legal, asesoramiento y gestión de reclamaciones derivadas de la circulación.'),
('auto', 'Robo', 0, '15', 'Indemniza el robo del vehículo o el intento con daños, así como la sustracción de piezas fijas del coche.'),
('auto', 'Lunas', 0, '12', 'Reparación o sustitución de parabrisas, luneta y ventanillas por rotura o impacto.'),
('auto', 'Vehículo de sustitución', 0, '18', 'Proporciona un coche de cortesía durante la reparación por un siniestro cubierto.'),
('auto', 'Daños propios', 0, '35', 'Cubre daños del propio vehículo por colisión, salida de vía o vandalismo, con franquicia según póliza.'),

('hogar', 'Incendio', 1, '0', 'Cubre daños por incendio, humo o explosión en el continente y/o contenido de la vivienda.'),
('hogar', 'Daños por agua', 1, '0', 'Cubre daños derivados de fugas, roturas de tuberías y filtraciones accidentales dentro del hogar.'),
('hogar', 'Responsabilidad civil', 1, '0', 'Cubre daños a terceros causados por la vivienda o por los ocupantes en la vida privada.'),
('hogar', 'Robo en vivienda', 0, '20', 'Indemniza robo o intento con daños, incluyendo cerraduras, puertas y bienes sustraídos.'),
('hogar', 'Todo riesgo accidental', 0, '30', 'Cubre daños accidentales del contenido dentro del hogar (golpes, caídas, roturas).'),
('hogar', 'Asistencia en el hogar', 0, '10', 'Servicios de urgencia: fontanería, electricidad, cerrajería y reparaciones básicas.'),

('salud', 'Medicina general', 1, '0', 'Consultas de medicina general, seguimiento de patologías leves y derivaciones a especialistas.'),
('salud', 'Especialistas', 1, '0', 'Acceso a especialistas y pruebas diagnósticas prescritas dentro del cuadro médico.'),
('salud', 'Urgencias', 1, '0', 'Atención de urgencias médicas ambulatorias u hospitalarias durante las 24 horas.'),
('salud', 'Hospitalización', 0, '25', 'Cubre estancia hospitalaria, intervenciones quirúrgicas y cuidados asociados.'),
('salud', 'Dental', 0, '15', 'Servicios odontológicos básicos y descuentos en tratamientos avanzados.'),
('salud', 'Reembolso de gastos', 0, '30', 'Reintegro parcial de gastos médicos fuera del cuadro concertado, según condiciones.'),

('viaje', 'Asistencia médica en viaje', 1, '0', 'Cubre gastos médicos, hospitalarios y farmacéuticos durante el viaje por enfermedad o accidente.'),
('viaje', 'Repatriación', 1, '0', 'Traslado sanitario o repatriación al país de origen por causa médica justificada.'),
('viaje', 'Pérdida de equipaje', 1, '0', 'Indemnización por pérdida, robo o daño del equipaje facturado o en custodia.'),
('viaje', 'Cancelación de viaje', 0, '20', 'Reembolso de gastos no recuperables por causas justificadas y documentadas.'),
('viaje', 'Deportes de aventura', 0, '18', 'Extiende la cobertura a actividades de riesgo durante el viaje.'),
('viaje', 'Responsabilidad civil en viaje', 0, '10', 'Cubre daños a terceros ocasionados durante el viaje en el ámbito personal.');

SET @sql := IF(
  @has_precioExtra > 0 AND @has_precio_extra > 0,
  'UPDATE ins_coverages SET precioExtra = COALESCE(precio_extra, precioExtra)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Reglas de riesgo
INSERT INTO ins_risk_rules (tipo_seguro, campo, operador, valor, accion, mensaje) VALUES
('auto', 'edad', '<', '18', 'reject', 'No aseguramos conductores menores de 18 años.'),
('salud', 'edad', '<', '18', 'reject', 'No podemos asegurar menores de 18 años en salud.'),
('viaje', 'edad', '<', '18', 'reject', 'No podemos asegurar menores de 18 años en viaje.'),
('salud', 'patologias_graves', '=', 'true', 'reject', 'No podemos asegurar con patologías graves declaradas.'),
('viaje', 'duracion_dias', '>', '90', 'recargo', 'Estancia superior a 90 días (recargo).');

-- Reglas de pricing
INSERT INTO ins_pricing_rules (tipo_seguro, condicion, factor, descripcion) VALUES
('auto', '{"edad_joven":"true"}', 1.30, 'Edad joven'),
('salud', '{"edad_joven":"true"}', 1.30, 'Edad joven'),
('viaje', '{"edad_joven":"true"}', 1.30, 'Edad joven'),
('hogar', '{"zona_riesgo":"true"}', 1.25, 'Zona de riesgo detectada'),
('viaje', '{"destino_usa":"true"}', 1.40, 'Destino USA');
