-- Phase 1: logistics domain. Adjust the version number (V10) so it sorts after your existing migrations.

CREATE TABLE driver (
    id             BIGSERIAL    PRIMARY KEY,
    full_name      VARCHAR(120) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    license_number VARCHAR(40)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_driver_license UNIQUE (license_number)
);

CREATE TABLE vehicle (
    id          BIGSERIAL     PRIMARY KEY,
    plate       VARCHAR(20)   NOT NULL,
    capacity_kg NUMERIC(10,2) NOT NULL,
    capacity_m3 NUMERIC(10,2) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    driver_id   BIGINT        REFERENCES driver (id),
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_vehicle_plate UNIQUE (plate),
    CONSTRAINT ck_vehicle_capacity_kg CHECK (capacity_kg > 0),
    CONSTRAINT ck_vehicle_capacity_m3 CHECK (capacity_m3 > 0)
);
-- a driver can be assigned to at most one vehicle
CREATE UNIQUE INDEX uq_vehicle_driver ON vehicle (driver_id) WHERE driver_id IS NOT NULL;

CREATE TABLE delivery (
    id                  BIGSERIAL        PRIMARY KEY,
    order_id            BIGINT           NOT NULL,   -- TODO: add FK to your orders table once you confirm its name
    status              VARCHAR(20)      NOT NULL,
    destination_lat     DOUBLE PRECISION NOT NULL,
    destination_lng     DOUBLE PRECISION NOT NULL,
    destination_address VARCHAR(255),
    window_start        TIMESTAMPTZ,
    window_end          TIMESTAMPTZ,
    weight_kg           NUMERIC(10,2)    NOT NULL,
    volume_m3           NUMERIC(10,2)    NOT NULL,
    estimated_arrival   TIMESTAMPTZ,
    version             BIGINT           NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_delivery_lat    CHECK (destination_lat BETWEEN -90 AND 90),
    CONSTRAINT ck_delivery_lng    CHECK (destination_lng BETWEEN -180 AND 180),
    CONSTRAINT ck_delivery_weight CHECK (weight_kg >= 0),
    CONSTRAINT ck_delivery_volume CHECK (volume_m3 >= 0),
    CONSTRAINT ck_delivery_window CHECK (window_start IS NULL OR window_end IS NULL OR window_end > window_start)
);
CREATE INDEX idx_delivery_order  ON delivery (order_id);
CREATE INDEX idx_delivery_status ON delivery (status);

-- append-only audit log (no UPDATE endpoints exist for it)
CREATE TABLE delivery_event (
    id          BIGSERIAL    PRIMARY KEY,
    delivery_id BIGINT       NOT NULL REFERENCES delivery (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_delivery_event_delivery ON delivery_event (delivery_id, created_at);

CREATE TABLE route (
    id               BIGSERIAL   PRIMARY KEY,
    vehicle_id       BIGINT      NOT NULL REFERENCES vehicle (id),
    status           VARCHAR(20) NOT NULL,
    planned_start    TIMESTAMPTZ,
    total_distance_m BIGINT,
    total_duration_s BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- a vehicle can have only ONE open (PLANNED or ACTIVE) route. Enforced by the DB so races can't break it.
CREATE UNIQUE INDEX uq_route_one_open_per_vehicle
    ON route (vehicle_id) WHERE status IN ('PLANNED', 'ACTIVE');

CREATE TABLE route_stop (
    id            BIGSERIAL   PRIMARY KEY,
    route_id      BIGINT      NOT NULL REFERENCES route (id) ON DELETE CASCADE,
    delivery_id   BIGINT      NOT NULL REFERENCES delivery (id),
    stop_sequence INT         NOT NULL,
    status        VARCHAR(20) NOT NULL,
    planned_arrival TIMESTAMPTZ,
    CONSTRAINT ck_stop_sequence CHECK (stop_sequence >= 1),
    CONSTRAINT uq_stop_route_sequence UNIQUE (route_id, stop_sequence),
    CONSTRAINT uq_stop_route_delivery UNIQUE (route_id, delivery_id)
);

CREATE TABLE vehicle_position (
    id          BIGSERIAL        PRIMARY KEY,
    vehicle_id  BIGINT           NOT NULL REFERENCES vehicle (id),
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    speed_kmh   DOUBLE PRECISION,
    heading     DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ      NOT NULL,
    received_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_pos_lat     CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_pos_lng     CHECK (lng BETWEEN -180 AND 180),
    CONSTRAINT ck_pos_heading CHECK (heading IS NULL OR heading BETWEEN 0 AND 360)
);
CREATE INDEX idx_position_vehicle_time ON vehicle_position (vehicle_id, recorded_at DESC);
