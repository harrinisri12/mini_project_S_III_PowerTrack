# PowerTrack: Smart Electricity Outage Detection and Restoration System

PowerTrack is an academic electricity distribution grid management system designed for regional grid coordination (modeled on the Tamil Nadu electrical network). It provides automated electrical fault simulation, priority-based outage queue management (with immediate priority overrides for hospitals and emergency facilities), field technician dispatch coordination, and real-time grid restoration tracking with persistent PostgreSQL database integration.

---

## 1. What PowerTrack Does

PowerTrack simulates and manages the complete lifecycle of electricity distribution outages:
1. **Grid Monitoring & Topology:** Models regional substations, distribution transformers, and feeders as a graph network.
2. **Consumer Directory & Criticality:** Maintains consumer records, identifying essential facilities (hospitals, trauma centers, emergency services).
3. **Fault Simulation:** Simulates equipment failures (e.g., transformer trips, feeder cable faults) and calculates downstream affected areas.
4. **Dynamic Priority Queue:** Automatically evaluates outage severity; outages affecting hospitals/emergency clinics are assigned **HIGH** priority and placed at the head of the restoration queue.
5. **Two-Role Operational Dashboards:**
   - **Government Dashboard:** Administrative grid oversight, fault simulation, crew assignment, consumer audits, and historical logs (direct restoration restricted to field technicians).
   - **Technician Dashboard:** Field crew workspace for inspecting assigned outages, updating on-site progress, executing grid restoration, and logging repair notes.
6. **Full Database Persistence:** Every outage, dispatch, consumer status, and restoration log is persistently saved to a Supabase PostgreSQL database using JDBC transactions.

---

## 2. Project Folder Structure & Architecture

```
mini project III/
├── .env.example                       # Sanitized template for database credentials
├── .env                               # Active environment file (ignored in Git)
├── .gitignore                         # Excludes .env, *.class, and logs
├── README.md                          # Project documentation and review guide
│
├── frontend/                          # Web Frontend (No heavy frameworks, clean HTML/CSS/JS)
│   ├── index.html                     # Two-role landing page, Government & Technician dashboards
│   ├── script.js                      # Hash routing (#government, #technician), API calls, DOM rendering
│   └── style.css                      # Unified color theme using CSS variables
│
└── backend/                           # Java Backend (Lightweight, native JDK HttpServer, JDBC)
    ├── PowerTrackServer.java          # Primary Web Application Server entry point (port 8080)
    ├── Main.java                      # Alternative Interactive Console (CLI) entry point for viva demo
    │
    ├── lib/
    │   └── postgresql-42.7.3.jar      # Official PostgreSQL JDBC Driver
    │
    ├── database/
    │   ├── DatabaseConnection.java    # Reusable JDBC connection manager with SSL & IPv4 support
    │   ├── schema.sql                 # Canonical PostgreSQL database schema (8 core tables & sample data)
    │   └── schema_adjustments.sql     # Optional non-destructive migrations for existing databases
    │
    ├── datastructure/
    │   ├── Queue.java                 # Custom Linked-List Priority Queue (implements priority ordering)
    │   └── Graph.java                 # Custom Adjacency List Graph (BFS downstream impact & DFS pathing)
    │
    ├── model/
    │   ├── Consumer.java              # Consumer model with criticality levels (HIGH, MEDIUM, LOW)
    │   ├── NetworkNode.java           # Electrical asset model (Substations, Transformers, Feeders)
    │   ├── NetworkConnection.java     # Power line link between two network nodes
    │   ├── Outage.java                # Outage model implementing Comparable<Outage> for priority sorting
    │   └── Technician.java            # Field technician model with availability tracking
    │
    ├── dao/
    │   ├── ConsumerDAO.java           # JDBC queries for consumers and locality power updates
    │   ├── NetworkDAO.java            # JDBC queries for network nodes and transmission connections
    │   ├── TechnicianDAO.java         # JDBC queries for technician availability and assignments
    │   ├── OutageDAO.java             # Transactional JDBC operations for outages and restoration logs
    │   └── StatsDAO.java              # Live grid metrics calculated directly from database records
    │
    └── service/
        └── ElectricitySystem.java     # Core system coordinator linking data structures, DAOs, and business logic
```

---

## 3. How Frontend Communicates with Backend

1. **Lightweight Built-In Server:** The backend uses Java's native `com.sun.net.httpserver.HttpServer` listening on port `8080` (no heavy third-party web frameworks like Spring Boot are required).
2. **REST API Endpoints:** All requests use JSON payloads over HTTP:
   - `GET /api/network`: Retrieves all grid nodes and topology connections.
   - `GET /api/consumers`: Retrieves consumer accounts and criticality levels.
   - `GET /api/technicians`: Retrieves field workforce availability.
   - `GET /api/outages/active`: Retrieves active outages and queue rankings.
   - `GET /api/outages/history`: Retrieves completed historical restorations.
   - `POST /api/outages/simulate`: Triggers a simulated equipment fault.
   - `POST /api/outages/process`: Dispatches the highest-priority queued outage.
   - `POST /api/outages/assign`: Manually assigns a technician to an outage.
   - `POST /api/outages/update-status`: Technician updates field progress stage.
   - `POST /api/outages/restore`: Restores power, re-energizes consumers, and logs history.
   - `GET /api/stats`: Live grid status counts (nodes, powered consumers, active outages).
   - `GET /api/health`: Database connectivity check (`UP` or `DOWN`).
3. **Static File Serving:** The root path (`/`) serves `frontend/index.html`, `frontend/style.css`, and `frontend/script.js`.
4. **No Authentication Screen:** Direct role selection between Government and Technician dashboards using hash navigation (`#government`, `#technician`).

---

## 4. How Java Connects to PostgreSQL Using JDBC

1. **JDBC Driver:** Located at `backend/lib/postgresql-42.7.3.jar`. Loaded into the JVM via `Class.forName("org.postgresql.Driver")`.
2. **Connection Utility (`DatabaseConnection.java`):**
   - Reads database configuration from `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD`.
   - Automatically ensures Supabase SSL mode (`sslmode=require`).
   - Formats connection strings to `jdbc:postgresql://`.
   - Strips sensitive passwords from exception messages before logging.
3. **Data Access Objects (DAOs):**
   - Uses `PreparedStatement` with parameterized placeholders (`?`) to prevent SQL injection.
   - Uses atomic transactions (`conn.setAutoCommit(false)`, `conn.commit()`, and `conn.rollback()`) so that when an outage is restored, all updates (outage status, consumer power, network component, technician status, audit log) succeed together.

---

## 5. Purpose of Key Data Structures

During project review, you can explain each data structure as follows:

| Data Structure | Implementation File | Role in PowerTrack |
| :--- | :--- | :--- |
| **Queue** | `datastructure/Queue.java` | **Priority Queue:** Manages active outages. Outages implementing `Comparable<Outage>` are sorted upon insertion so that emergencies affecting hospitals appear at the front (head) of the queue. |
| **Graph** | `datastructure/Graph.java` | **Grid Topology:** Represents the electrical network as an Adjacency List (`HashMap<String, ArrayList<String>>`). Provides **BFS** to analyze cascading downstream feeder outages and **DFS** to trace electrical line paths. |
| **ArrayList** | `java.util.ArrayList` | **Dynamic Collections:** Stores lists of affected areas, affected consumer IDs, critical facility tags, and database result sets without fixed array size limits. |
| **HashMap** | `java.util.HashMap` | **O(1) Lookups:** Stores adjacency neighbor mappings in `Graph`, environment variables in `DatabaseConnection`, and connection maps in `PowerTrackServer`. |

---

## 6. Outage Priority & Technician Restoration Logic

### Priority Evaluation (`ElectricitySystem.java`)
When a fault is simulated on a network node (e.g., `DT-01` or `FEEDER-01`):
1. The system identifies all downstream locality areas affected using the Graph.
2. The consumer registry is scanned for accounts in those areas.
3. If an affected consumer has **HIGH** criticality (e.g., *Apollo Hospital*, *Government General Hospital*, *Emergency Trauma Clinic*), the entire outage is assigned **HIGH** priority.
4. Commercial/industrial loads result in **MEDIUM** priority; residential loads result in **LOW** priority.
5. In the Priority Queue (`Queue.java`), HIGH priority outages jump to the front of the line.

### Field Restoration (`OutageDAO.java`)
When a technician clicks **Mark as Restored** in the Technician Dashboard:
1. `outages.status` is set to `RESTORED` and `restored_at` is set to `NOW()`.
2. `network_nodes.is_active` is set back to `TRUE`.
3. `consumers.power_status` in the affected areas is set back to `TRUE` (`POWER ON`).
4. The technician is marked as `AVAILABLE` (`true`).
5. A permanent record is inserted into `restoration_history`.
6. Both dashboards automatically reflect the updated status on their next refresh.

---

## 7. How to Configure the Database Safely

1. Locate `.env.example` at the project root.
2. Copy it to create `.env` (or configure `backend/.env`):
   ```powershell
   Copy-Item .env.example backend\.env
   ```
3. Fill in your Supabase connection parameters:
   ```ini
   SUPABASE_DB_URL=jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require
   SUPABASE_DB_USER=postgres.xxxxxxxxxxxxxxxxxxxx
   SUPABASE_DB_PASSWORD=your_actual_password_here
   ```
4. **Security Rule:** `.env` is listed in `.gitignore` and must never be committed to GitHub or shared publicly.

---

## 8. How to Compile and Run the Application

Open **Windows PowerShell** and navigate to the project directory:

### Step 1: Navigate to the `backend` directory
```powershell
cd "d:\mini project\mini project III\backend"
```

### Step 2: Compile All Java Files from Scratch
```powershell
javac -cp ".;lib/postgresql-42.7.3.jar" -d . model/*.java datastructure/*.java database/*.java dao/*.java service/*.java PowerTrackServer.java Main.java
```

### Step 3A: Start the Web Application (Primary)
```powershell
java -cp ".;lib/postgresql-42.7.3.jar" PowerTrackServer
```
Open your browser and navigate to:
```
http://localhost:8080
```

### Step 3B: Alternative Interactive Console Demo (CLI Viva Demo)
If an examiner asks to see the core Java algorithms in the terminal without a browser:
```powershell
java -cp ".;lib/postgresql-42.7.3.jar" Main
```

### Stopping the Application
In PowerShell, press `CTRL + C` in the running terminal window.

---

## 9. Database Initialization & Schema Adjustments

- **Canonical Schema (`backend/database/schema.sql`):**
  Contains the complete table definitions for the 8 core tables:
  1. `network_nodes`
  2. `network_connections`
  3. `consumers`
  4. `technicians`
  5. `outages`
  6. `outage_affected_consumers`
  7. `technician_assignments`
  8. `restoration_history`
  Along with default sample data for the regional grid. Run this script in the Supabase SQL Editor when initializing a fresh database.

- **Schema Adjustments (`backend/database/schema_adjustments.sql`):**
  A non-destructive script that adds optional metadata columns (`priority_reason`, `critical_facilities`, `queue_sequence`) and performance indexes to an existing database without dropping or resetting any data. Run this only if your existing database lacks these helper columns.
