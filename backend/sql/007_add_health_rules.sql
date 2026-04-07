-- Reglas adicionales para salud (edad máxima y fumador).
INSERT INTO ins_risk_rules (tipo_seguro, campo, operador, valor, accion, mensaje)
SELECT 'salud', 'edad', '>', '74', 'reject', 'No aseguramos en salud a mayores de 75 años.'
WHERE NOT EXISTS (
  SELECT 1 FROM ins_risk_rules
  WHERE tipo_seguro = 'salud'
    AND campo = 'edad'
    AND operador = '>'
    AND valor = '74'
);

INSERT INTO ins_risk_rules (tipo_seguro, campo, operador, valor, accion, mensaje)
SELECT 'salud', 'fumador', '=', 'true', 'recargo', 'Fumador (recargo).'
WHERE NOT EXISTS (
  SELECT 1 FROM ins_risk_rules
  WHERE tipo_seguro = 'salud'
    AND campo = 'fumador'
    AND operador = '='
    AND valor = 'true'
);

INSERT INTO ins_risk_rules (tipo_seguro, campo, operador, valor, accion, mensaje)
SELECT 'salud', 'patologias_graves', '=', 'true', 'reject',
       'No podemos asegurar en salud con patologías graves declaradas.'
WHERE NOT EXISTS (
  SELECT 1 FROM ins_risk_rules
  WHERE tipo_seguro = 'salud'
    AND campo = 'patologias_graves'
    AND operador = '='
    AND valor = 'true'
);
