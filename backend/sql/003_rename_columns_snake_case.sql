-- Rename camelCase columns to snake_case for consistency with Spring naming.

ALTER TABLE `ins_base_prices`
  CHANGE COLUMN `tipoSeguro` `tipo_seguro` VARCHAR(255) NOT NULL,
  CHANGE COLUMN `precioBase` `precio_base` DOUBLE NOT NULL;

ALTER TABLE `ins_coverages`
  CHANGE COLUMN `tipoSeguro` `tipo_seguro` VARCHAR(255) NOT NULL,
  CHANGE COLUMN `precioExtra` `precio_extra` VARCHAR(255) NOT NULL;

ALTER TABLE `ins_pricing_rules`
  CHANGE COLUMN `tipoSeguro` `tipo_seguro` VARCHAR(255) NOT NULL;

ALTER TABLE `ins_risk_rules`
  CHANGE COLUMN `tipoSeguro` `tipo_seguro` VARCHAR(255) NOT NULL;

ALTER TABLE `ins_insurance_subtypes`
  CHANGE COLUMN `tipoSeguro` `tipo_seguro` VARCHAR(255) NOT NULL;

ALTER TABLE `ins_insurance_usages`
  CHANGE COLUMN `tipoSeguro` `tipo_seguro` VARCHAR(255) NOT NULL;

ALTER TABLE `ins_projects`
  CHANGE COLUMN `tipoSeguro` `tipo_seguro` VARCHAR(255) NULL;
