-- ==============================================================================
-- PowerTrack: Smart Electricity Outage Detection & Restoration System
-- Supabase PostgreSQL Relational Schema & Idempotent Sample Data Initialization
-- ==============================================================================

-- 1. NETWORK NODES TABLE (Substations, Feeders, Transformers, Locality Areas)
CREATE TABLE IF NOT EXISTS network_nodes (
    node_id VARCHAR(50) PRIMARY KEY,
    node_name VARCHAR(255) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    grid_locality_area VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. NETWORK CONNECTIONS TABLE (Grid Transmission & Distribution Topology)
CREATE TABLE IF NOT EXISTS network_connections (
    connection_id SERIAL PRIMARY KEY,
    source_node_id VARCHAR(50) NOT NULL REFERENCES network_nodes(node_id) ON DELETE CASCADE,
    destination_node_id VARCHAR(50) NOT NULL REFERENCES network_nodes(node_id) ON DELETE CASCADE,
    connection_type VARCHAR(50) DEFAULT 'TRANSMISSION',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_network_connections UNIQUE (source_node_id, destination_node_id)
);

-- 3. TECHNICIANS TABLE (Field Workforce Roster)
CREATE TABLE IF NOT EXISTS technicians (
    technician_id INT PRIMARY KEY,
    technician_name VARCHAR(100) NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    availability_status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. CONSUMERS TABLE (Registered Electricity Accounts & Facilities)
CREATE TABLE IF NOT EXISTS consumers (
    consumer_id INT PRIMARY KEY,
    account_name VARCHAR(255) NOT NULL,
    category_type VARCHAR(50) NOT NULL,
    criticality_level VARCHAR(20) NOT NULL CHECK (criticality_level IN ('HIGH', 'MEDIUM', 'LOW')),
    grid_locality_area VARCHAR(50) NOT NULL REFERENCES network_nodes(node_id),
    connected_load_kw NUMERIC(10, 2) DEFAULT 5.00,
    power_status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. OUTAGES TABLE (Incident Tracking, Priority Classification & Lifecycle)
CREATE TABLE IF NOT EXISTS outages (
    outage_id INT PRIMARY KEY,
    fault_component_id VARCHAR(50) NOT NULL REFERENCES network_nodes(node_id),
    fault_type VARCHAR(255) NOT NULL,
    affected_area TEXT NOT NULL,
    affected_consumer_count INT NOT NULL DEFAULT 0,
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    priority_reason TEXT,
    critical_facilities TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'IN PROGRESS', 'RESTORED')),
    assigned_technician_id INT REFERENCES technicians(technician_id),
    assigned_technician_name VARCHAR(100),
    queue_sequence INT NOT NULL DEFAULT 0,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    restored_at TIMESTAMPTZ NULL
);

-- 6. OUTAGE AFFECTED CONSUMERS TABLE (Associative Junction Table)
CREATE TABLE IF NOT EXISTS outage_affected_consumers (
    outage_id INT NOT NULL REFERENCES outages(outage_id) ON DELETE CASCADE,
    consumer_id INT NOT NULL REFERENCES consumers(consumer_id) ON DELETE CASCADE,
    PRIMARY KEY (outage_id, consumer_id)
);

-- 7. TECHNICIAN ASSIGNMENTS TABLE (Dispatch History & Status)
CREATE TABLE IF NOT EXISTS technician_assignments (
    assignment_id SERIAL PRIMARY KEY,
    outage_id INT NOT NULL REFERENCES outages(outage_id) ON DELETE CASCADE,
    technician_id INT NOT NULL REFERENCES technicians(technician_id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assignment_status VARCHAR(50) NOT NULL DEFAULT 'ASSIGNED'
);

-- 8. RESTORATION HISTORY TABLE (Audit Log of Completed Restorations)
CREATE TABLE IF NOT EXISTS restoration_history (
    history_id SERIAL PRIMARY KEY,
    outage_id INT NOT NULL REFERENCES outages(outage_id) ON DELETE CASCADE,
    technician_id INT REFERENCES technicians(technician_id),
    restoration_details TEXT NOT NULL,
    restored_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ==============================================================================
-- INDEXES FOR FREQUENTLY QUERIED COLUMNS
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_outages_status ON outages(status);
CREATE INDEX IF NOT EXISTS idx_outages_priority ON outages(priority);
CREATE INDEX IF NOT EXISTS idx_outages_reported_at ON outages(reported_at);
CREATE INDEX IF NOT EXISTS idx_outages_queue_seq ON outages(queue_sequence);
CREATE INDEX IF NOT EXISTS idx_consumers_locality ON consumers(grid_locality_area);
CREATE INDEX IF NOT EXISTS idx_consumers_criticality ON consumers(criticality_level);
CREATE INDEX IF NOT EXISTS idx_consumers_power_status ON consumers(power_status);
CREATE INDEX IF NOT EXISTS idx_network_conn_src ON network_connections(source_node_id);
CREATE INDEX IF NOT EXISTS idx_network_conn_dst ON network_connections(destination_node_id);
CREATE INDEX IF NOT EXISTS idx_outage_affected_outage ON outage_affected_consumers(outage_id);
CREATE INDEX IF NOT EXISTS idx_outage_affected_consumer ON outage_affected_consumers(consumer_id);
CREATE INDEX IF NOT EXISTS idx_tech_assign_outage ON technician_assignments(outage_id);
CREATE INDEX IF NOT EXISTS idx_restoration_history_outage ON restoration_history(outage_id);

-- ==============================================================================
-- IDEMPOTENT SAMPLE DATA SEEDING (TAMIL NADU REGIONAL GRID)
-- ==============================================================================

-- 1. SEED NETWORK NODES
INSERT INTO network_nodes (node_id, node_name, node_type, grid_locality_area, is_active)
VALUES
    ('SS-ERD-01', 'Erode Central 230kV Substation', 'SUBSTATION', 'AREA-ERD-PR', TRUE),
    ('SS-PER-02', 'Perundurai 110kV Substation', 'SUBSTATION', 'AREA-PER-SIP', TRUE),
    ('SS-TPR-03', 'Tiruppur North 110kV Substation', 'SUBSTATION', 'AREA-TPR-RAY', TRUE),
    ('FD-ERD-01', 'Erode North Feeder', 'FEEDER', 'AREA-ERD-PR', TRUE),
    ('FD-PER-02', 'SIPCOT Industrial Feeder', 'FEEDER', 'AREA-PER-SIP', TRUE),
    ('FD-CHN-03', 'Chennimalai Feeder', 'FEEDER', 'AREA-CHN-TWN', TRUE),
    ('FD-AVI-04', 'Avinashi Feeder', 'FEEDER', 'AREA-AVI-BYP', TRUE),
    ('FD-TPR-05', 'Rayapuram Feeder', 'FEEDER', 'AREA-TPR-RAY', TRUE),
    ('DT-ERD-101', 'Kumalan Kuttai Transformer', 'TRANSFORMER', 'AREA-ERD-PR', TRUE),
    ('DT-PER-102', 'SIPCOT Phase-I Transformer', 'TRANSFORMER', 'AREA-PER-SIP', TRUE),
    ('DT-CHN-103', 'Chennimalai Town Transformer', 'TRANSFORMER', 'AREA-CHN-TWN', TRUE),
    ('DT-AVI-104', 'Avinashi Main Transformer', 'TRANSFORMER', 'AREA-AVI-BYP', TRUE),
    ('DT-TPR-105', 'Rayapuram Textile Transformer', 'TRANSFORMER', 'AREA-TPR-RAY', TRUE),
    ('AREA-ERD-PR', 'Perundurai Road Locality', 'AREA', 'AREA-ERD-PR', TRUE),
    ('AREA-ERD-SN', 'Sampath Nagar Locality', 'AREA', 'AREA-ERD-SN', TRUE),
    ('AREA-PER-SIP', 'SIPCOT Industrial Zone', 'AREA', 'AREA-PER-SIP', TRUE),
    ('AREA-CHN-TWN', 'Chennimalai Town Area', 'AREA', 'AREA-CHN-TWN', TRUE),
    ('AREA-AVI-BYP', 'Avinashi Bypass Area', 'AREA', 'AREA-AVI-BYP', TRUE),
    ('AREA-TPR-RAY', 'Rayapuram Textile Cluster', 'AREA', 'AREA-TPR-RAY', TRUE)
ON CONFLICT (node_id) DO UPDATE SET
    node_name = EXCLUDED.node_name,
    node_type = EXCLUDED.node_type;

-- 2. SEED NETWORK CONNECTIONS
INSERT INTO network_connections (source_node_id, destination_node_id, connection_type)
VALUES
    ('SS-ERD-01', 'SS-PER-02', 'INTER_TIE'),
    ('SS-PER-02', 'SS-TPR-03', 'INTER_TIE'),
    ('SS-ERD-01', 'FD-ERD-01', 'SUBSTATION_TO_FEEDER'),
    ('SS-PER-02', 'FD-PER-02', 'SUBSTATION_TO_FEEDER'),
    ('SS-PER-02', 'FD-CHN-03', 'SUBSTATION_TO_FEEDER'),
    ('SS-TPR-03', 'FD-AVI-04', 'SUBSTATION_TO_FEEDER'),
    ('SS-TPR-03', 'FD-TPR-05', 'SUBSTATION_TO_FEEDER'),
    ('FD-ERD-01', 'DT-ERD-101', 'FEEDER_TO_TRANSFORMER'),
    ('FD-PER-02', 'DT-PER-102', 'FEEDER_TO_TRANSFORMER'),
    ('FD-CHN-03', 'DT-CHN-103', 'FEEDER_TO_TRANSFORMER'),
    ('FD-AVI-04', 'DT-AVI-104', 'FEEDER_TO_TRANSFORMER'),
    ('FD-TPR-05', 'DT-TPR-105', 'FEEDER_TO_TRANSFORMER'),
    ('DT-ERD-101', 'AREA-ERD-PR', 'TRANSFORMER_TO_AREA'),
    ('DT-ERD-101', 'AREA-ERD-SN', 'TRANSFORMER_TO_AREA'),
    ('DT-PER-102', 'AREA-PER-SIP', 'TRANSFORMER_TO_AREA'),
    ('DT-CHN-103', 'AREA-CHN-TWN', 'TRANSFORMER_TO_AREA'),
    ('DT-AVI-104', 'AREA-AVI-BYP', 'TRANSFORMER_TO_AREA'),
    ('DT-TPR-105', 'AREA-TPR-RAY', 'TRANSFORMER_TO_AREA')
ON CONFLICT (source_node_id, destination_node_id) DO NOTHING;

-- 3. SEED TECHNICIANS
INSERT INTO technicians (technician_id, technician_name, specialization, availability_status)
VALUES
    (201, 'R. Suresh', 'Electrical Lines', TRUE),
    (202, 'M. Senthil Kumar', 'Transformers', TRUE),
    (203, 'K. Mani', 'Distribution', TRUE),
    (204, 'P. Gunasekaran', 'Transformers', TRUE),
    (205, 'S. Murugesan', 'Electrical Lines', TRUE),
    (206, 'V. Anandhan', 'Distribution', TRUE)
ON CONFLICT (technician_id) DO NOTHING;

-- 4. SEED CONSUMERS (CRITICALITY RULES: HOSPITALS/EMERGENCY = HIGH, COMMERCIAL/INDUSTRIAL = MEDIUM, RESIDENTIAL = LOW)
INSERT INTO consumers (consumer_id, account_name, category_type, criticality_level, grid_locality_area, connected_load_kw, power_status)
VALUES
    (1001, 'S. Karthikeyan (Domestic Connection)', 'RESIDENTIAL', 'LOW', 'AREA-ERD-PR', 3.50, TRUE),
    (1002, 'Erode Govt Headquarters Hospital & Trauma ICU', 'HOSPITAL', 'HIGH', 'AREA-ERD-PR', 85.00, TRUE),
    (1003, 'V. Meenakshi Sundaram (Commercial Complex)', 'COMMERCIAL', 'MEDIUM', 'AREA-ERD-SN', 22.00, TRUE),
    (1004, 'Sampath Nagar Community Health Center & Maternity Ward', 'COMMUNITY_HEALTH', 'HIGH', 'AREA-ERD-SN', 40.00, TRUE),
    (1005, 'K. Rangarajan (Domestic Connection)', 'RESIDENTIAL', 'LOW', 'AREA-ERD-SN', 4.00, TRUE),
    (1006, 'Sakthi Murugan Spinning Mills Ltd.', 'INDUSTRIAL', 'MEDIUM', 'AREA-PER-SIP', 120.00, TRUE),
    (1007, 'Perundurai SIPCOT Water Supply & Effluent Treatment Plant', 'WATER_FACILITY', 'HIGH', 'AREA-PER-SIP', 95.00, TRUE),
    (1008, 'G. Thangavel - Sri Krishna Textiles', 'INDUSTRIAL', 'MEDIUM', 'AREA-PER-SIP', 65.00, TRUE),
    (1009, 'P. Dhanalakshmi (SIPCOT Staff Quarters)', 'RESIDENTIAL', 'LOW', 'AREA-PER-SIP', 5.00, TRUE),
    (1010, 'Chennimalai Handloom Weavers Co-op Society', 'COMMERCIAL', 'MEDIUM', 'AREA-CHN-TWN', 18.00, TRUE),
    (1011, 'Murugan Temple Bazaar Commercial Complex', 'COMMERCIAL', 'MEDIUM', 'AREA-CHN-TWN', 28.00, TRUE),
    (1012, 'N. Balasubramanian (Domestic Connection)', 'RESIDENTIAL', 'LOW', 'AREA-CHN-TWN', 3.50, TRUE),
    (1013, 'Chennimalai Govt Higher Secondary School', 'EDUCATION', 'MEDIUM', 'AREA-CHN-TWN', 15.00, TRUE),
    (1014, 'Avinashi Agro Logistics Hub', 'COMMERCIAL', 'MEDIUM', 'AREA-AVI-BYP', 45.00, TRUE),
    (1015, 'R. Selvakumar Auto Works', 'COMMERCIAL', 'MEDIUM', 'AREA-AVI-BYP', 12.00, TRUE),
    (1016, 'Avinashi Emergency Response & Fire Service Station', 'EMERGENCY_SERVICE', 'HIGH', 'AREA-AVI-BYP', 35.00, TRUE),
    (1017, 'T. Muthusamy (Domestic Connection)', 'RESIDENTIAL', 'LOW', 'AREA-AVI-BYP', 4.50, TRUE),
    (1018, 'Tiruppur Export Knitwear Unit No. 4', 'INDUSTRIAL', 'MEDIUM', 'AREA-TPR-RAY', 95.00, TRUE),
    (1019, 'Rayapuram Dyeing & Processing Works', 'INDUSTRIAL', 'MEDIUM', 'AREA-TPR-RAY', 110.00, TRUE),
    (1020, 'K. Jayanthi - Jayam Garments', 'COMMERCIAL', 'MEDIUM', 'AREA-TPR-RAY', 25.00, TRUE),
    (1021, 'Tiruppur Govt Medical College Hospital & Oxygen Plant', 'HOSPITAL', 'HIGH', 'AREA-TPR-RAY', 90.00, TRUE),
    (1022, 'A. Palanisamy (Domestic Connection)', 'RESIDENTIAL', 'LOW', 'AREA-TPR-RAY', 4.00, TRUE)
ON CONFLICT (consumer_id) DO UPDATE SET
    criticality_level = EXCLUDED.criticality_level,
    category_type = EXCLUDED.category_type;

-- 5. SEED HISTORICAL COMPLETED OUTAGES (ARCHIVED / RESTORED AUDIT LOG)
-- Only inserted if not already present. Never overwrites or resets active outages.
INSERT INTO outages (
    outage_id, fault_component_id, fault_type, affected_area, affected_consumer_count,
    priority, priority_reason, critical_facilities, status, assigned_technician_id, assigned_technician_name,
    queue_sequence, reported_at, restored_at
)
VALUES
    (
        1001, 'FD-ERD-01', '33kV Feeder Tripping via Earth Fault', 'AREA-ERD-PR, AREA-ERD-SN', 5,
        'HIGH', 'HIGH PRIORITY: Essential medical facilities affected (Erode Govt Headquarters Hospital & Trauma ICU, Sampath Nagar Community Health Center & Maternity Ward).',
        '[HIGH] Erode Govt Headquarters Hospital & Trauma ICU, [HIGH] Sampath Nagar Community Health Center & Maternity Ward',
        'RESTORED', 201, 'R. Suresh', 1, NOW() - INTERVAL '5 hours', NOW() - INTERVAL '3 hours'
    ),
    (
        1002, 'DT-CHN-103', 'Distribution Transformer Thermal Overload', 'AREA-CHN-TWN', 4,
        'MEDIUM', 'MEDIUM PRIORITY: Important community facility affected (Chennimalai Govt Higher Secondary School).',
        '[MEDIUM] Chennimalai Govt Higher Secondary School',
        'RESTORED', 204, 'P. Gunasekaran', 2, NOW() - INTERVAL '4 hours', NOW() - INTERVAL '2 hours'
    ),
    (
        1003, 'DT-PER-102', 'HT Bushing Insulator Flashover', 'AREA-PER-SIP', 4,
        'HIGH', 'HIGH PRIORITY: Essential public utility affected (Perundurai SIPCOT Water Supply & Effluent Treatment Plant).',
        '[HIGH] Perundurai SIPCOT Water Supply & Effluent Treatment Plant',
        'RESTORED', 202, 'M. Senthil Kumar', 3, NOW() - INTERVAL '3 hours', NOW() - INTERVAL '90 minutes'
    ),
    (
        1004, 'FD-AVI-04', '11kV Overhead Conductor Snap', 'AREA-AVI-BYP', 4,
        'HIGH', 'HIGH PRIORITY: Emergency response and fire service facility affected (Avinashi Emergency Response & Fire Service Station).',
        '[HIGH] Avinashi Emergency Response & Fire Service Station',
        'RESTORED', 205, 'S. Murugesan', 4, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '45 minutes'
    ),
    (
        1005, 'DT-TPR-105', 'Transformer Oil Deterioration & Trip', 'AREA-TPR-RAY', 5,
        'HIGH', 'HIGH PRIORITY: Essential hospital facility affected (Tiruppur Govt Medical College Hospital & Oxygen Plant).',
        '[HIGH] Tiruppur Govt Medical College Hospital & Oxygen Plant',
        'RESTORED', 202, 'M. Senthil Kumar', 5, NOW() - INTERVAL '90 minutes', NOW() - INTERVAL '20 minutes'
    )
ON CONFLICT (outage_id) DO NOTHING;

-- 6. SEED OUTAGE AFFECTED CONSUMERS FOR HISTORICAL OUTAGES
INSERT INTO outage_affected_consumers (outage_id, consumer_id)
VALUES
    (1001, 1001), (1001, 1002), (1001, 1003), (1001, 1004), (1001, 1005),
    (1002, 1010), (1002, 1011), (1002, 1012), (1002, 1013),
    (1003, 1006), (1003, 1007), (1003, 1008), (1003, 1009),
    (1004, 1014), (1004, 1015), (1004, 1016), (1004, 1017),
    (1005, 1018), (1005, 1019), (1005, 1020), (1005, 1021), (1005, 1022)
ON CONFLICT (outage_id, consumer_id) DO NOTHING;

-- 7. SEED RESTORATION HISTORY AUDIT RECORDS
INSERT INTO restoration_history (history_id, outage_id, technician_id, restoration_details, restored_at)
VALUES
    (1, 1001, 201, '33kV Feeder earth-fault relay reset and circuit re-energized. Hospital power restored.', NOW() - INTERVAL '3 hours'),
    (2, 1002, 204, 'Distribution transformer cooling cycle completed and phase fuses replaced.', NOW() - INTERVAL '2 hours'),
    (3, 1003, 202, 'HT bushing insulator cleaned, flashover gap repaired, and SIPCOT water supply normalized.', NOW() - INTERVAL '90 minutes'),
    (4, 1004, 205, 'Snapped 11kV conductor spliced, re-tensioned, and Avinashi Fire Station feed energized.', NOW() - INTERVAL '45 minutes'),
    (5, 1005, 202, 'Transformer oil filtered and topped up. Oxygen plant power fully restored.', NOW() - INTERVAL '20 minutes')
ON CONFLICT (history_id) DO NOTHING;
