--liquibase formatted sql

--changeset liquibase:020-sync-export-flag logicalFilePath:migrations/020-sync-export-flag.sql
-- Client.Export.SyncExport.Enable — gates the synchronous service-order export
-- (GET /api/service-orders/export and the UI's Download CSV). Seeded off.
INSERT INTO feature_properties (id, property_key, property_value, description)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'Client.Export.SyncExport.Enable', CAST('false' AS JSON),
       'When true the service-order sync export (Download CSV) is available'
WHERE NOT EXISTS (SELECT 1 FROM feature_properties WHERE property_key = 'Client.Export.SyncExport.Enable');
--rollback DELETE FROM feature_properties WHERE property_key = 'Client.Export.SyncExport.Enable';
