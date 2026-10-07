import java.util.Scanner;

import service.ElectricitySystem;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        ElectricitySystem system =
            new ElectricitySystem();

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

                    System.out.println(
                        "Available Distribution Components:"
                    );

                    System.out.println(
                        "FD-ERD-01  - Erode North Feeder"
                    );

                    System.out.println(
                        "FD-PER-02  - SIPCOT Industrial Feeder"
                    );

                    System.out.println(
                        "FD-CHN-03  - Chennimalai Feeder"
                    );

                    System.out.println(
                        "FD-AVI-04  - Avinashi Feeder"
                    );

                    System.out.println(
                        "FD-TPR-05  - Rayapuram Feeder"
                    );

                    System.out.println(
                        "DT-ERD-101 - Kumalan Kuttai Transformer"
                    );

                    System.out.println(
                        "DT-PER-102 - SIPCOT Phase-I Transformer"
                    );

                    System.out.println(
                        "DT-CHN-103 - Chennimalai Town Transformer"
                    );

                    System.out.println(
                        "DT-AVI-104 - Avinashi Main Transformer"
                    );

                    System.out.println(
                        "DT-TPR-105 - Rayapuram Textile Transformer"
                    );

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