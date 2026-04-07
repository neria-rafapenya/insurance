-- Plantillas de prompts para el flujo del paso 1 (configurable por idioma).
CREATE TABLE IF NOT EXISTS ins_prompt_templates (
  id INT AUTO_INCREMENT PRIMARY KEY,
  step VARCHAR(50) NOT NULL,
  template_key VARCHAR(100) NOT NULL,
  language VARCHAR(10) NOT NULL,
  tipo_seguro VARCHAR(50) NULL,
  template TEXT NOT NULL,
  activo TINYINT(1) DEFAULT 1
);

-- Limpiar las plantillas base del paso 1 para evitar duplicados.
DELETE FROM ins_prompt_templates WHERE step = 'step1';

INSERT INTO ins_prompt_templates (step, template_key, language, tipo_seguro, template, activo) VALUES
(
  'step1',
  'welcome_tipo',
  'es',
  NULL,
  'Te damos la bienvenida al validador de seguros, vamos a hacerte algunas preguntas para poder ayudarte mejor.\n\n¿Qué tipo de seguro deseas contratar? ¿auto, hogar, salud, viaje?',
  1
),
(
  'step1',
  'ask_subtipo',
  'es',
  NULL,
  '¿Cuál es el nivel de protección que deseas para {tipo}? {subtipos}',
  1
),
(
  'step1',
  'ask_subtipo_hogar',
  'es',
  'hogar',
  '¿Eres el propietario del inmueble, o eres inquilino?',
  1
),
(
  'step1',
  'ask_uso',
  'es',
  NULL,
  '¿Qué uso tiene? {usos}',
  1
),
(
  'step1',
  'ask_destino',
  'es',
  'viaje',
  '¿Cuál es el destino del viaje?',
  1
),
(
  'step1',
  'ask_ubicacion',
  'es',
  NULL,
  '¿En qué ciudad o zona se ubicará el riesgo?',
  1
);

-- Plantillas de prompts para el flujo del paso 2 (Datos del riesgo).
DELETE FROM ins_prompt_templates WHERE step = 'step2';

INSERT INTO ins_prompt_templates (step, template_key, language, tipo_seguro, template, activo) VALUES
(
  'step2',
  'auto_intro',
  'es',
  'auto',
  'Necesitamos saber qué vehículo quieres asegurar (coche, moto...), tu edad y qué uso le vas a dar.',
  1
),
(
  'step2',
  'auto_specs',
  'es',
  'auto',
  'Queremos saber más de tu vehículo, dinos la marca, modelo, año y potencia en caballos.',
  1
),
(
  'step2',
  'auto_usage',
  'es',
  'auto',
  'Nos dijiste que el uso que das a tu vehículo es {auto_uso}, ahora dinos cuántos km tiene y si lo aparcas en garaje o en la calle.',
  1
),
(
  'step2',
  'home_intro',
  'es',
  'hogar',
  'Ya sabemos que tu vivienda es en {home_tenencia}, ahora necesitamos saber cómo es. Dinos si es tipo piso, dúplex, chalet, local, trastero...; dinos también los metros cuadrados y el año de construcción aproximado.',
  1
),
(
  'step2',
  'home_location',
  'es',
  'hogar',
  'Antes nos dijiste que usarías la vivienda como {home_uso}, pero necesitamos que nos digas la ubicación, código postal, ciudad, provincia y si hay contenido, qué valor en euros consideras que tiene.',
  1
),
(
  'step2',
  'health_age',
  'es',
  'salud',
  'Necesitamos saber tu edad. ¿Eres fumador?',
  1
),
(
  'step2',
  'health_path',
  'es',
  'salud',
  '¿Tienes alguna patología relevante? (sí/no + lista)',
  1
),
(
  'step2',
  'health_plan',
  'es',
  'salud',
  'La modalidad será ¿individual o familiar?',
  1
),
(
  'step2',
  'health_family',
  'es',
  'salud',
  '¿Cuántas personas vais a asegurar y qué edades tienen? Si alguna es fumadora o tiene patologías relevantes, indícalo junto a su edad.',
  1
),
(
  'step2',
  'travel_scope',
  'es',
  'viaje',
  'Tu viaje es nacional o internacional? ¿A qué sitio quieres viajar?',
  1
),
(
  'step2',
  'travel_people',
  'es',
  'viaje',
  '¿Cuántas personas quieres asegurar para tu viaje?',
  1
),
(
  'step2',
  'travel_ages',
  'es',
  'viaje',
  'Dinos la edad de cada uno de los compañeros de viaje (la tuya incluida).',
  1
),
(
  'step2',
  'travel_duration',
  'es',
  'viaje',
  '¿Qué duración tendrá tu estancia?',
  1
),
(
  'step2',
  'travel_purpose',
  'es',
  'viaje',
  '¿Es un viaje de ocio o de trabajo?',
  1
);
