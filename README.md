# PowerTrack: Smart Electricity Outage Detection and Restoration System

PowerTrack is an electricity distribution grid management system designed for regional grid coordination (modeled on the Tamil Nadu regional electrical network). It provides automated electrical fault simulation, dynamic downstream Breadth-First Search (BFS) impact analysis, priority-based outage queue management (with immediate priority overrides for hospitals and essential facilities), field technician dispatch coordination, and real-time grid restoration tracking with persistent PostgreSQL database integration via JDBC.

---

## 1. System Overview

PowerTrack manages the complete lifecycle of electricity distribution grid incidents:
1. **Grid Monitoring & Topology:** Models regional substations, distribution feeders, transformers, and locality areas as a directed graph.
2. **Consumer Directory & Criticality:** Maintains consumer accounts and authoritative criticality ratings (Hospitals/ICU, Emergency/Water, Public/Schools, Residential, Commercial/Industrial).
3. **Downstream BFS Fault Simulation:** Simulates equipment faults (e.g. transformer thermal overload, feeder line trips) and dynamically discovers all cascading downstream distribution areas using Breadth-First Search.
4. **Deterministic Priority Queue:** Automatically evaluates outage severity; incidents affecting healthcare and emergency facilities are assigned **HIGH** priority (Tier 5 / Tier 4) and placed at the head of the restoration queue.
5. **Two-Role Operational Dashboards:**
   - **Government Dashboard:** Administrative grid oversight, fault simulation, crew assignment, consumer audits, and historical logs. Direct restoration is strictly restricted to field crews.
   - **Technician Dashboard:** Field crew workspace for inspecting assigned outages, acknowledging repairs in progress, executing grid restoration, and recording repair audit notes.
6. **Full Database Persistence:** Every outage, crew dispatch, consumer status update, and restoration log is persistently saved to Supabase PostgreSQL using ACID-compliant JDBC transactions with connection pooling.

---

## 2. Project Directory Structure

```
mini_project_S_III_PowerTrack/
├── .env.example                       # Sanitized template for database environment variables
├── .env                               # Local database credentials (excluded from Git)
├── .gitignore                         # Comprehensive ignore rules (.env, *.class, IDE, OS files)
├── README.md                          # Project documentation and setup guide
│
├── frontend/                          # Clean Vanilla Web Frontend (HTML5, CSS3, Vanilla ES6+)
│   ├── index.html                     # Two-role landing page, Government & Technician dashboards
│   ├── script.js                      # Hash routing (#government, #technician), deduplicated API calls, UI rendering
│   └── style.css                      # Unified design system using CSS custom properties
│
└── backend/                           # Java Backend (JDK HttpServer, JDBC, custom data structures)
    ├── PowerTrackServer.java          # Primary Web Application Server entry point (port 8080)
    ├── Main.java                      # Interactive Console (CLI) test harness entry point for viva demos
    │
    ├── lib/
    │   └── postgresql-42.7.3.jar      # Official PostgreSQL JDBC Driver dependency
    │
    ├── database/
    │   ├── DatabaseConnection.java    # Managed JDBC connection pool (max 12 connections, dynamic proxies)
    │   ├── schema.sql                 # Canonical PostgreSQL schema (8 core tables & sample data)
    │   └── schema_adjustments.sql     # Non-destructive schema helper script for existing databases
    │
    ├── datastructure/
    │   ├── Queue.java                 # Custom Linked-List Priority Queue (implements priority ordering & FIFO)
    │   └── Graph.java                 # Custom Adjacency-List Graph (directed downstream BFS & DFS pathing)
    │
    ├── model/
    │   ├── Consumer.java              # Consumer model with authoritative database criticality tiers
    │   ├── NetworkNode.java           # Electrical asset model (Substations, Feeders, Transformers, Areas)
    │   ├── NetworkConnection.java     # Directed power line transmission connection
    │   ├── Outage.java                # Outage model implementing Comparable<Outage> for priority sorting
    │   └── Technician.java            # Field technician model with availability tracking
    │
    ├── dao/
    │   ├── ConsumerDAO.java           # JDBC operations for consumers and locality power updates
    │   ├── NetworkDAO.java            # JDBC operations for network nodes and grid connections
    │   ├── TechnicianDAO.java         # JDBC operations for technician roster and availability
    │   ├── OutageDAO.java             # Transactional JDBC operations with joined technician queries
    │   └── StatsDAO.java              # Single-query live grid metrics with short-TTL caching
    │
    └── service/
        └── ElectricitySystem.java     # Core system coordinator linking data structures, DAOs, and business logic
```

---

## 3. Technology Stack

- **Frontend:** Semantic HTML5, CSS3 Custom Properties, Vanilla JavaScript (ES6+ with in-flight request deduplication). No external npm/Node dependencies.
- **Backend:** Java SE (JDK 17 or later, compatible with JDK 21+), native `com.sun.net.httpserver.HttpServer`.
- **Database:** PostgreSQL (hosted on Supabase) via JDBC.
- **Dependencies:** `postgresql-42.7.3.jar` (packaged under `backend/lib/`).

---

## 4. Setup and Configuration

### Prerequisites
- Java Development Kit (JDK 17 or higher). Verify with:
  ```powershell
  javac -version
  java -version
  ```
- Active Supabase PostgreSQL database project.

### Step 1: Configure Environment Variables
Copy `.env.example` to `.env` in the project root:
```powershell
Copy-Item .env.example .env
```
Open `.env` and fill in your Supabase connection parameters:
```ini
SUPABASE_DB_URL=jdbc:postgresql://your-project-ref.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.your-project-ref
SUPABASE_DB_PASSWORD=your_database_password
```

### Step 2: Initialize Database Schema (First-time Setup)
1. Open your Supabase Dashboard and go to the **SQL Editor**.
2. If setting up a fresh database, run the contents of `backend/database/schema.sql`.
3. If using an existing database, run `backend/database/schema_adjustments.sql` to ensure all metadata columns and performance indexes exist.

---

## 5. Compilation and Execution

Open **PowerShell** or Command Prompt in the project root directory:

### Step 1: Navigate to the `backend` folder
```powershell
cd backend
```

### Step 2: Compile All Java Sources
```powershell
javac -cp ".;lib/postgresql-42.7.3.jar" *.java dao/*.java database/*.java datastructure/*.java model/*.java service/*.java
```

### Step 3A: Start the Web Application Server (Primary)
```powershell
java -cp ".;lib/postgresql-42.7.3.jar" PowerTrackServer
```
Once started, open your web browser and visit:
```
http://localhost:8080
```

### Step 3B: Interactive Terminal Console Demo (CLI Demo for Viva)
To demonstrate core Java data structures (Graph BFS, Priority Queue, Crew Dispatch) directly in the terminal without a browser:
```powershell
java -cp ".;lib/postgresql-42.7.3.jar" Main
```

### Stopping the Server
Press `Ctrl + C` in the terminal to trigger graceful shutdown.

---

## 6. Architecture & Core Data Structures

| Data Structure | Source File | Function in PowerTrack |
| :--- | :--- | :--- |
| **Priority Queue** | `datastructure/Queue.java` | Custom linked-list priority queue with O(1) peek/dequeue. Highest-priority incidents (hospitals/ICU) jump to the head of the queue. Equal-priority incidents follow strict FIFO ordering. |
| **Directed Graph** | `datastructure/Graph.java` | Adjacency-list graph representing power flow (`Substation → Feeder → Transformer → Area`). Downstream Breadth-First Search (BFS) dynamically computes cascading failure impact without hardcoded maps. |
| **Connection Pool** | `database/DatabaseConnection.java` | Controlled connection pool with dynamic reflection proxies that intercept `close()` to recycle physical connections. |
| **Single-Query Aggregator** | `dao/StatsDAO.java` | Consolidates grid dashboard counts into a single SQL statement with short-TTL caching. |

---

## 7. Security Best Practices

- **Zero-Credential Commits:** Actual `.env` files are ignored by `.gitignore`. Only `.env.example` containing placeholder values is tracked in version control.
- **Error Sanitization:** `DatabaseConnection.sanitizeErrorMessage()` automatically masks database passwords before logging error messages.
- **SQL Injection Prevention:** All DAO queries strictly use parameterized `PreparedStatement` queries.
