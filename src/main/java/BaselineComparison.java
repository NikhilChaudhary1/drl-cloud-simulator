import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BaselineComparison {

    // --- CONSTANTS ---
    private static final int HOSTS = 5;
    private static final int VMS = 5;
    private static final int CLOUDLETS = 500;
    private static final double COST_PER_SEC = 0.1;
    private static final long SEED = 42;

    private static class SimResult {
        String name;
        double avgTurnaround;
        double avgWait;
        double cost;

        SimResult(String name, double avgTurnaround, double avgWait, double cost) {
            this.name = name;
            this.avgTurnaround = avgTurnaround;
            this.avgWait = avgWait;
            this.cost = cost;
        }
    }

    private static List<SimResult> finalResults = new ArrayList<>();

    public static void main(String[] args) {
        // High-level suppression for speed
        Log.setLevel(ch.qos.logback.classic.Level.ERROR);

        System.out.println("============================================");
        System.out.printf("STARTING FAST COMPARISON (%d Tasks)\n", CLOUDLETS);
        System.out.println("============================================\n");

        runSimulation("Round Robin");
        runSimulation("FCFS (Single VM)");

        printComparisonTable();
    }

    private static void runSimulation(String algorithm) {
        CloudSimPlus simulation = new CloudSimPlus();
        Random random = new Random(SEED);

        createRandomDatacenter(simulation, random);
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vmList = createRandomVms(random);
        List<Cloudlet> cloudletList = createRandomWorkload(random);

        if (algorithm.equals("Round Robin")) {
            for (int i = 0; i < cloudletList.size(); i++) {
                cloudletList.get(i).setVm(vmList.get(i % vmList.size()));
            }
        } else {
            // FCFS: Force all tasks into VM 0 to show the bottleneck
            for (Cloudlet c : cloudletList) {
                c.setVm(vmList.get(0));
            }
        }

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        System.out.printf("[System] Running %s... ", algorithm);
        long start = System.currentTimeMillis();
        simulation.start();
        System.out.printf("Done (%d ms)\n", (System.currentTimeMillis() - start));

        storeSummary(algorithm, broker.getCloudletFinishedList());
    }

    private static void storeSummary(String algoName, List<Cloudlet> finishedList) {
        double totalTurnaround = 0, totalWait = 0, totalCost = 0;

        for (Cloudlet c : finishedList) {
            double arrivalTime = c.getSubmissionDelay();
            totalTurnaround += (c.getFinishTime() - arrivalTime);
            totalWait += (c.getExecStartTime() - arrivalTime);
            totalCost += (c.getFinishTime() - c.getExecStartTime()) * COST_PER_SEC;
        }

        finalResults.add(new SimResult(
                algoName,
                totalTurnaround / finishedList.size(),
                totalWait / finishedList.size(),
                totalCost
        ));
    }

    private static void printComparisonTable() {
        System.out.println("\n" + "=".repeat(75));
        System.out.println("            FINAL PERFORMANCE COMPARISON (FAST TEST)");
        System.out.println("=".repeat(75));
        System.out.printf("%-20s | %-12s | %-12s | %-10s\n", "ALGORITHM", "AVG TURN (s)", "AVG WAIT (s)", "TOTAL COST");
        System.out.println("-".repeat(75));

        for (SimResult res : finalResults) {
            System.out.printf("%-20s | %-12.2f | %-12.2f | $%-10.2f\n",
                    res.name, res.avgTurnaround, res.avgWait, res.cost);
        }
        System.out.println("=".repeat(75));
    }

    private static void createRandomDatacenter(CloudSimPlus simulation, Random random) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            // Boosted MIPS to 10k for speed
            peList.add(new PeSimple(10000));
            hostList.add(new HostSimple(32768, 1000000, 1000000, peList));
        }
        new DatacenterSimple(simulation, hostList).getCharacteristics().setCostPerSecond(COST_PER_SEC);
    }

    private static List<Vm> createRandomVms(Random random) {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < VMS; i++) {
            // High speed VMs
            Vm vm = new VmSimple(5000, 1);
            vm.setRam(1024).setBw(1000).setSize(10000);
            list.add(vm);
        }
        return list;
    }

    private static List<Cloudlet> createRandomWorkload(Random random) {
        List<Cloudlet> list = new ArrayList<>();
        double currentArrivalTime = 0;
        for (int i = 0; i < CLOUDLETS; i++) {
            // Moderate task lengths (10k to 50k)
            long length = 10000 + random.nextInt(40000);
            Cloudlet c = new CloudletSimple(length, 1);
            currentArrivalTime += 0.01; // Fast arrival to build a small queue
            c.setSubmissionDelay(currentArrivalTime);
            list.add(c);
        }
        return list;
    }
}