-- Optional: run only in a fresh database after schema.sql.
-- Alternatively the app can seed once with --mysql --init-demo.
USE serc;
START TRANSACTION;
INSERT INTO users VALUES
 ('citizen-1','Demo Citizen','CITIZEN','fake-contact'),
 ('dispatcher-1','Demo Dispatcher','DISPATCHER','fake-contact'),
 ('admin-1','Demo Admin','ADMIN','fake-contact');
INSERT INTO response_teams VALUES
 ('AMB-1','AMBULANCE','AVAILABLE',3,4), ('AMB-2','AMBULANCE','AVAILABLE',6,8),
 ('FIRE-1','FIRE_TRUCK','AVAILABLE',2,3), ('FIRE-2','FIRE_TRUCK','AVAILABLE',8,7),
 ('POL-1','POLICE','AVAILABLE',0,1), ('POL-2','POLICE','AVAILABLE',9,9);
UPDATE app_metadata SET revision=revision+1,saved_at=CURRENT_TIMESTAMP(6) WHERE id=1;
COMMIT;
