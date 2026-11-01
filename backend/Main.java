import java.util.Scanner;

import service.ElectricitySystem;
import database.DatabaseConnection;

/**
 * ==============================================================================
 * PowerTrack: Alternative Interactive Console (CLI) Entry Point
 * ==============================================================================
 * Purpose in Project:
 *   - Serves as an interactive terminal-based test harness for college viva & demos.
 *   - Allows demonstrating core Java algorithms (Priority Queue, Graph traversals,
 *     fault simulation, and crew dispatch) directly without needing a browser.
 *   - The primary web application entry point is PowerTrackServer.java.
 * ==============================================================================
 */
public class Main {

    public static void main(String[] args) {

        System.out.println();
        System.out.println("==============================================");
        System.out.println("                 POWERTRACK");
        System.out.println("   SMART ELECTRICITY OUTAGE SYSTEM (TAMIL NADU)");
        System.out.println("==============================================");

        if (!DatabaseConnection.isConfigured()) {
            System.err.println();
            System.err.println("[PowerTrack Fatal Error] Supabase database credentials are not configured.");
            System.err.println("Required environment variables: SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD.");
            System.err.println("In-memory offline fallback is completely disabled. Startup halted.");
            System.err.println("==============================================");
            System.exit(1);
            return;
        }

        if (!DatabaseConnection.testConnection()) {
            System.err.println();
            System.err.println("[PowerTrack Fatal Error] Cannot connect to Supabase PostgreSQL database.");
            System.err.println("Please verify database status, network connectivity, and credentials.");
            System.err.println("In-memory offline fallback is completely disabled. Startup halted.");
            System.err.println("==============================================");
            System.exit(1);
            return;
        }

        ElectricitySystem system;
        try {
            system = new ElectricitySystem();
        } catch (Exception e) {
            System.err.println();
            System.err.println("[PowerTrack Fatal Error] Failed to load data from Supabase: "
                    + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
            System.err.println("In-memory offline fallback is completely disabled. Startup halted.");
            System.err.println("==============================================");
            System.exit(1);
            return;
        }

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println();
            System.out.println(
                "=============================================="
            );

            System.out.println(
                "                 POWERTRACK"
            );

            System.out.println(
                "   SMART ELECTRICITY OUTAGE SYSTEM (TAMIL NADU)"
            );

            System.out.println(
                "=============================================="
            );

            System.out.println(
                "1. View Electricity Network (Grid Topology)"
            );

            System.out.println(
                "2. View Registered Consumers"
            );

            System.out.println(
                "3. View Field Technicians"
            );

            System.out.println(
                "4. Simulate Fault Detection"
            );

            System.out.println(
                "5. View Active Outages & Queue"
            );

            System.out.println(
                "6. Process Next Outage (Dispatch Crew)"
            );

            System.out.println(
                "7. Restore Outage (Power Normalized)"
            );

            System.out.println(
                "8. View Outage History & Audit Log"
            );

            System.out.println(
                "0. Exit"
            );

            System.out.println(
                "=============================================="
            );

            System.out.print(
                "Enter your choice: "
            );

            int choice = scanner.nextInt();

            switch (choice) {

                // =================================
                // VIEW NETWORK
                // =================================

                case 1:

                    system.displayNetwork();

                    break;

                // =================================
                // VIEW CONSUMERS
                // =================================

                case 2:

                    system.displayConsumers();

                    break;

                // =================================
                // VIEW TECHNICIANS
                // =================================

                case 3:

                    system.displayTechnicians();

                    break;

                // =================================
                // SIMULATE FAULT
                // =================================

                case 4:

                    System.out.println();

                    System.out.println(
                        "========== SIMULATE FAULT =========="
                    );

                    System.out.println();

                    system.displayAvailableComponents();

                    System.out.println();

                    System.out.print(
                        "Enter component to fail: "
                    );

                    scanner.nextLine();

                    String component =
                        scanner.nextLine()
                            .trim()
                            .toUpperCase();

                    system.simulateFault(
                        component
                    );

                    break;

                // =================================
                // ACTIVE OUTAGES
                // =================================

                case 5:

                    system.displayActiveOutages();

                    break;

                // =================================
                // PROCESS NEXT OUTAGE
                // =================================

                case 6:

                    system.processNextOutage();

                    break;

                // =================================
                // RESTORE OUTAGE
                // =================================

                case 7:

                    System.out.println();

                    System.out.println(
                        "========== RESTORE OUTAGE =========="
                    );

                    System.out.print(
                        "Enter outage ID: "
                    );

                    int outageId =
                        scanner.nextInt();

                    system.restoreOutage(
                        outageId
                    );

                    break;

                // =================================
                // OUTAGE HISTORY
                // =================================

                case 8:

                    system.displayOutageHistory();

                    break;

                // =================================
                // EXIT
                // =================================

                case 0:

                    System.out.println();

                    System.out.println(
                        "Thank you for using "
                        + "PowerTrack."
                    );

                    scanner.close();

                    return;

                // =================================
                // INVALID
                // =================================

                default:

                    System.out.println();

                    System.out.println(
                        "Invalid choice. "
                        + "Please try again."
                    );
            }
        }
    }
}