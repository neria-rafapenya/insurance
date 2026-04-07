ALTER TABLE `ins_insurance_subtypes`
  ADD COLUMN `pregunta_asistente` VARCHAR(255) NULL AFTER `nombre`,
  ADD COLUMN `sinonimos_json` JSON NULL AFTER `pregunta_asistente`;

UPDATE `ins_insurance_subtypes`
SET `pregunta_asistente` = '¿Eres el propietario del inmueble, o eres inquilino?'
WHERE `tipo_seguro` = 'hogar';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'rc',
  'basico',
  'responsabilidad civil',
  'third party',
  'third-party'
)
WHERE `tipo_seguro` = 'auto' AND `nombre` = 'terceros';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'ampliado',
  'terceros plus',
  'third party plus',
  'extended third party'
)
WHERE `tipo_seguro` = 'auto' AND `nombre` = 'terceros ampliado';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'todo riesgo',
  'all risk',
  'all-risk',
  'full coverage',
  'comprehensive'
)
WHERE `tipo_seguro` = 'auto' AND `nombre` = 'todo riesgo';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'dueno',
  'titular',
  'propiedad',
  'owner',
  'landlord'
)
WHERE `tipo_seguro` = 'hogar' AND `nombre` = 'propietario';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'arrendatario',
  'alquiler',
  'tenant',
  'renter'
)
WHERE `tipo_seguro` = 'hogar' AND `nombre` = 'inquilino';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'solo',
  'una persona',
  'individual'
)
WHERE `tipo_seguro` = 'salud' AND `nombre` = 'individual';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'familia',
  'familiar',
  'family'
)
WHERE `tipo_seguro` = 'salud' AND `nombre` = 'familiar';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'unico',
  'ocasional',
  'single trip',
  'one-off'
)
WHERE `tipo_seguro` = 'viaje' AND `nombre` = 'puntual';

UPDATE `ins_insurance_subtypes`
SET `sinonimos_json` = JSON_ARRAY(
  'anual',
  'todo el ano',
  'annual',
  'yearly'
)
WHERE `tipo_seguro` = 'viaje' AND `nombre` = 'anual';
