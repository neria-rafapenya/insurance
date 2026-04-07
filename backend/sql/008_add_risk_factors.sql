-- Tabla de factores de riesgo (configurables por cliente).
CREATE TABLE IF NOT EXISTS ins_risk_factors (
  id INT AUTO_INCREMENT PRIMARY KEY,
  tipo_seguro VARCHAR(50) NULL,
  campo VARCHAR(100) NOT NULL,
  fuente VARCHAR(100) NOT NULL,
  tipo_match VARCHAR(30) NOT NULL,
  valor_match TEXT NOT NULL,
  valor_resultado VARCHAR(100) NULL,
  prioridad INT DEFAULT 0,
  activo TINYINT(1) DEFAULT 1
);

-- Limpiar factores base anteriores para evitar duplicados.
DELETE FROM ins_risk_factors
WHERE campo IN ('zona_riesgo', 'destino_usa', 'patologias_graves', 'fumador', 'edad_joven');

-- Edad joven (aplica a todos).
INSERT INTO ins_risk_factors (tipo_seguro, campo, fuente, tipo_match, valor_match, valor_resultado, prioridad)
VALUES (NULL, 'edad_joven', 'edad_min', 'lt', '25', 'true', 100);

-- Zona de riesgo (hogar) por keywords.
INSERT INTO ins_risk_factors (tipo_seguro, campo, fuente, tipo_match, valor_match, valor_resultado, prioridad)
VALUES (
  'hogar',
  'zona_riesgo',
  'homeLocationContent',
  'keyword_any',
  JSON_ARRAY(
    'zona de riesgo',
    'alto riesgo',
    'alta siniestralidad',
    'zona inundable',
    'inundable',
    'inundacion',
    'costa',
    'costera',
    'playa',
    'rio',
    'barranco',
    'zona industrial',
    'poligono',
    'puerto',
    'centro',
    'casco antiguo',
    'zona conflictiva',
    'turistica'
  ),
  'true',
  90
);

-- Zona de riesgo (hogar) por prefijo de CP.
INSERT INTO ins_risk_factors (tipo_seguro, campo, fuente, tipo_match, valor_match, valor_resultado, prioridad)
VALUES (
  'hogar',
  'zona_riesgo',
  'homeLocationContent',
  'postal_prefix',
  JSON_ARRAY('08', '28', '46', '41', '29', '07', '03'),
  'true',
  80
);

-- Destino USA (viaje).
INSERT INTO ins_risk_factors (tipo_seguro, campo, fuente, tipo_match, valor_match, valor_resultado, prioridad)
VALUES (
  'viaje',
  'destino_usa',
  'travelDestination',
  'keyword_any',
  JSON_ARRAY('usa', 'eeuu', 'estados unidos', 'united states'),
  'true',
  90
);

-- Patologías graves (salud).
INSERT INTO ins_risk_factors (tipo_seguro, campo, fuente, tipo_match, valor_match, valor_resultado, prioridad)
VALUES (
  'salud',
  'patologias_graves',
  'healthPathologies',
  'keyword_any',
  JSON_ARRAY(
    'cancer',
    'oncolog',
    'infarto',
    'ictus',
    'transplante',
    'insuficiencia',
    'renal',
    'alzheimer',
    'parkinson',
    'ela',
    'esclerosis lateral',
    'cardiac',
    'epoc',
    'tumor',
    'metast',
    'sida',
    'vih',
    'hiv',
    'bronquitis'
  ),
  'true',
  90
);

-- Fumador (salud).
INSERT INTO ins_risk_factors (tipo_seguro, campo, fuente, tipo_match, valor_match, valor_resultado, prioridad)
VALUES (
  'salud',
  'fumador',
  'healthSmoker',
  'keyword_any',
  JSON_ARRAY('fumador', 'fumo', 'smoker', 'si', 'sí'),
  'true',
  90
);
