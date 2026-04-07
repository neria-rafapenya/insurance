-- Subtypes per insurance type
CREATE TABLE IF NOT EXISTS `ins_insurance_subtypes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `tipo_seguro` VARCHAR(255) NOT NULL,
  `nombre` VARCHAR(255) NOT NULL,
  `pregunta_asistente` VARCHAR(255) NULL,
  `sinonimos_json` JSON NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ins_insurance_subtypes_tipo` (`tipo_seguro`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `ins_insurance_subtypes` (`tipo_seguro`, `nombre`, `pregunta_asistente`, `sinonimos_json`) VALUES
('auto', 'terceros', NULL, JSON_ARRAY('rc', 'basico', 'responsabilidad civil', 'third party', 'third-party')),
('auto', 'terceros ampliado', NULL, JSON_ARRAY('ampliado', 'terceros plus', 'third party plus', 'extended third party')),
('auto', 'todo riesgo', NULL, JSON_ARRAY('todo riesgo', 'all risk', 'all-risk', 'full coverage', 'comprehensive')),
('hogar', 'propietario', '¿Eres el propietario del inmueble, o eres inquilino?', JSON_ARRAY('dueno', 'titular', 'propiedad', 'owner', 'landlord')),
('hogar', 'inquilino', '¿Eres el propietario del inmueble, o eres inquilino?', JSON_ARRAY('arrendatario', 'alquiler', 'tenant', 'renter')),
('salud', 'individual', NULL, JSON_ARRAY('solo', 'una persona', 'individual')),
('salud', 'familiar', NULL, JSON_ARRAY('familia', 'familiar', 'family')),
('viaje', 'puntual', NULL, JSON_ARRAY('unico', 'ocasional', 'single trip', 'one-off')),
('viaje', 'anual', NULL, JSON_ARRAY('anual', 'todo el ano', 'annual', 'yearly'));

-- Usage per insurance type
CREATE TABLE IF NOT EXISTS `ins_insurance_usages` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `tipo_seguro` VARCHAR(255) NOT NULL,
  `nombre` VARCHAR(255) NOT NULL,
  `categoria` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ins_insurance_usages_tipo` (`tipo_seguro`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `ins_insurance_usages` (`tipo_seguro`, `nombre`, `categoria`) VALUES
('auto', 'personal', 'uso'),
('auto', 'profesional', 'uso'),
('hogar', 'residencia habitual', 'uso'),
('hogar', 'segunda vivienda', 'uso'),
('viaje', 'ocio', 'tipo'),
('viaje', 'trabajo', 'tipo');
