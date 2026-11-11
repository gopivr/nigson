ALTER TABLE commerce.address
    ADD COLUMN latitude numeric(9,6),
    ADD COLUMN longitude numeric(9,6),
    ADD CONSTRAINT address_coordinates_pair_ck CHECK ((latitude IS NULL) = (longitude IS NULL)),
    ADD CONSTRAINT address_latitude_range_ck CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT address_longitude_range_ck CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180);
