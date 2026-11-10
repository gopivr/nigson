ALTER TABLE commerce.address
    DROP CONSTRAINT address_coordinates_pair_ck,
    DROP CONSTRAINT address_latitude_range_ck,
    DROP CONSTRAINT address_longitude_range_ck,
    DROP COLUMN latitude,
    DROP COLUMN longitude;
