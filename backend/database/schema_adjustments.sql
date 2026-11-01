-- ==============================================================================
-- PowerTrack: Non-Destructive Schema Adjustments & Query Optimization Indexes
-- ==============================================================================
-- IMPORTANT:
-- This script does NOT drop, recreate, or delete any data from your eight tables.
-- Run this in the Supabase SQL Editor to ensure optional helper columns
-- and high-performance database indexes exist for concurrent queries.

-- 1. Ensure network_nodes has is_active status column
ALTER TABLE network_nodes ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Ensure outages has priority reasoning, critical facilities, and assignment metadata
ALTER TABLE outages ADD COLUMN IF NOT EXISTS priority_reason TEXT;
ALTER TABLE outages ADD COLUMN IF NOT EXISTS critical_facilities TEXT;
ALTER TABLE outages ADD COLUMN IF NOT EXISTS queue_sequence INT NOT NULL DEFAULT 0;
ALTER TABLE outages ADD COLUMN IF NOT EXISTS assigned_technician_id INT REFERENCES technicians(technician_id);
ALTER TABLE outages ADD COLUMN IF NOT EXISTS assigned_technician_name VARCHAR(100);

-- 3. Ensure technicians has contact and assignment area helper columns
ALTER TABLE technicians ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE technicians ADD COLUMN IF NOT EXISTS email VARCHAR(100);
ALTER TABLE technicians ADD COLUMN IF NOT EXISTS assigned_area VARCHAR(100);

-- 4. High-performance indexes for frequent queries, status filters & joins
CREATE INDEX IF NOT EXISTS idx_outages_status ON outages(status);
CREATE INDEX IF NOT EXISTS idx_outages_priority ON outages(priority);
CREATE INDEX IF NOT EXISTS idx_outages_reported_at ON outages(reported_at);
CREATE INDEX IF NOT EXISTS idx_outages_queue_seq ON outages(queue_sequence);
CREATE INDEX IF NOT EXISTS idx_outages_fault_component ON outages(fault_component_id);
CREATE INDEX IF NOT EXISTS idx_outages_comp_status ON outages(fault_component_id, status);
CREATE INDEX IF NOT EXISTS idx_outages_status_reported ON outages(status, reported_at);

CREATE INDEX IF NOT EXISTS idx_consumers_locality ON consumers(grid_locality_area);
CREATE INDEX IF NOT EXISTS idx_consumers_criticality ON consumers(criticality_level);
CREATE INDEX IF NOT EXISTS idx_consumers_power_status ON consumers(power_status);

CREATE INDEX IF NOT EXISTS idx_technicians_availability ON technicians(availability_status);

CREATE INDEX IF NOT EXISTS idx_network_conn_src ON network_connections(source_node_id);
CREATE INDEX IF NOT EXISTS idx_network_conn_dst ON network_connections(destination_node_id);

CREATE INDEX IF NOT EXISTS idx_outage_affected_outage ON outage_affected_consumers(outage_id);
CREATE INDEX IF NOT EXISTS idx_outage_affected_consumer ON outage_affected_consumers(consumer_id);

CREATE INDEX IF NOT EXISTS idx_tech_assign_outage ON technician_assignments(outage_id);
CREATE INDEX IF NOT EXISTS idx_restoration_history_outage ON restoration_history(outage_id);
