-- Add descripcion field for coverage tooltips.
SET @has_descripcion := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ins_coverages'
    AND column_name = 'descripcion'
);

SET @sql := IF(
  @has_descripcion = 0,
  'ALTER TABLE ins_coverages ADD COLUMN descripcion LONGTEXT NULL AFTER nombre',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
