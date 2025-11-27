import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Baseline Comparison: "Ultimate Reality" Edition
 * Features: Massive scale, totally random hardware, random workload types.
 */
public class BaselineComparison {

    // --- SCALABILITY SETTINGS ---
    private static final int HOSTS = 10;
    private static final int VMS = 20;
    // Try 10,000 first. If your PC is fast, change to 100,000!
    private static final int CLOUDLETS = 750;

    private static final double COST_PER_SEC = 0.1;
    private static final long SEED = 42; // Fixed seed for fair comparison

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.printf("STARTING MASSIVE SIMULATION (%d Tasks)\n", CLOUDLETS);
        System.out.println("============================================");

        runSimulation("Round Robin");
        System.out.println("\n--------------------------------------------\n");
        runSimulation("FCFS");
    }

    private static void runSimulation(String algorithm) {
        System.out.printf("Initializing %s... ", algorithm);
        CloudSimPlus simulation = new CloudSimPlus();

        // Use same seed for both runs to ensure identical hardware & tasks
        Random random = new Random(SEED);

        // 1. Random Hardware
        createRandomDatacenter(simulation, random);

        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // 2. Random VMs
        List<Vm> vmList = createRandomVms(random);

        // 3. Random Workload
        List<Cloudlet> cloudletList = createRandomWorkload(random);

        // --- ALGORITHM LOGIC ---
        if (algorithm.equals("Round Robin")) {
            for (int i = 0; i < cloudletList.size(); i++) {
                Cloudlet c = cloudletList.get(i);
                // Circular assignment
                int vmIndex = i % vmList.size();
                c.setVm(vmList.get(vmIndex));
            }
        }
        // FCFS: Default behavior (Greedy/First-Fit)
        // -----------------------

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        System.out.println("Running...");
        simulation.start();

        printSummary(algorithm, broker.getCloudletFinishedList());
    }

    // --- 1. RANDOM DATACENTER GENERATOR ---
    private static void createRandomDatacenter(CloudSimPlus simulation, Random random) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            // Random Cores: Between 4 and 16 cores per host
            int cores = 4 + random.nextInt(12);
            // Random Speed: Between 1000 and 5000 MIPS per core
            long mips = 1000 + random.nextInt(4000);

            for (int j = 0; j < cores; j++) {
                peList.add(new PeSimple(mips));
            }

            // Random RAM: 16GB to 64GB
            long ram = 16384 + random.nextInt(49152);
            Host host = new HostSimple(ram, 1000000, 1000000, peList);
            hostList.add(host);
        }
        DatacenterSimple dc = new DatacenterSimple(simulation, hostList);
        dc.getCharacteristics().setCostPerSecond(COST_PER_SEC);
    }

    // --- 2. RANDOM VM GENERATOR ---
    private static List<Vm> createRandomVms(Random random) {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < VMS; i++) {
            // Random Speed: Some VMs are slow (500 MIPS), some fast (4000 MIPS)
            long mips = 500 + random.nextInt(3500);

            // Random Cores: 1 to 4 CPUs
            int pes = 1 + random.nextInt(4);

            Vm vm = new VmSimple(mips, pes);
            vm.setRam(1024).setBw(1000).setSize(10000);
            list.add(vm);
        }
        return list;
    }

    // --- 3. RANDOM WORKLOAD GENERATOR ---
    private static List<Cloudlet> createRandomWorkload(Random random) {
        List<Cloudlet> list = new ArrayList<>();
        double currentArrivalTime = 0;

        for (int i = 0; i < CLOUDLETS; i++) {
            long length;
            double r = random.nextDouble();

            // TASK TYPES:
            if (r < 0.5) {
                // 50% Small (Web Request): 1k - 5k MI
                length = 1000 + random.nextInt(4000);
            } else if (r < 0.9) {
                // 40% Medium (Data Process): 10k - 50k MI
                length = 10000 + random.nextInt(40000);
            } else {
                // 10% Huge (Video Render): 100k - 500k MI (These cause jams!)
                length = 100000 + random.nextInt(400000);
            }

            Cloudlet cloudlet = new CloudletSimple(length, 1);
            cloudlet.setFileSize(300);
            cloudlet.setOutputSize(300);

            // Random Arrival: Bursts and gaps
            // Average arrival: every 0.05 seconds (High throughput)
            currentArrivalTime += random.nextDouble() * 0.1;
            cloudlet.setSubmissionDelay(currentArrivalTime);

            list.add(cloudlet);
        }
        return list;
    }

    private static void printSummary(String algoName, List<Cloudlet> finishedList) {
        double totalCost = 0;
        double totalTurnaroundTime = 0;
        double totalWaitTime = 0;

        for (Cloudlet c : finishedList) {
            double executionTime = c.getFinishTime() - c.getExecStartTime();
            double arrivalTime = c.getSubmissionDelay();
            double turnaround = c.getFinishTime() - arrivalTime;
            double waitTime = c.getExecStartTime() - arrivalTime; // Time spent in Queue

            double cost = executionTime * COST_PER_SEC;

            totalTurnaroundTime += turnaround;
            totalCost += cost;
            totalWaitTime += waitTime;
        }

        double avgTurnaround = totalTurnaroundTime / finishedList.size();
        double avgWait = totalWaitTime / finishedList.size();

        System.out.println("============================================");
        System.out.printf("RESULTS FOR: %s\n", algoName.toUpperCase());
        System.out.println("============================================");
        System.out.printf("Tasks Processed:         %d / %d\n", finishedList.size(), CLOUDLETS);
        System.out.printf("Average Turnaround Time: %.2f sec\n", avgTurnaround);
        System.out.printf("Average Queue Wait Time: %.2f sec\n", avgWait);
        System.out.printf("TOTAL RESOURCE COST:     $%.2f\n", totalCost);
        System.out.println("============================================");
    }
}