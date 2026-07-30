-- Optional consulting room/cabin label shown on the Doctor Queue's per-doctor
-- display board (e.g. "Room 3") - no existing "Room" concept fits here, since
-- com.pms.ipadmission.entity.Room is inpatient bed/ward inventory, unrelated
-- to outpatient consulting rooms.
ALTER TABLE consultant ADD COLUMN consulting_room_label VARCHAR(50) NULL;
