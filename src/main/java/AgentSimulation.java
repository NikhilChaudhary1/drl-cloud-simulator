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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AgentSimulation {

    // --- CONFIGURATION ---
    private static final String STATE_FILE = "state.json";
    private static final String ACTION_FILE = "action.json";
    private static final String REWARD_FILE = "reward.json";

    private static final int HOSTS = 5;
    private static final int VMS = 5;
    private static final int CLOUDLETS = 500;
    private static final double COST_PER_SEC = 0.1;
    private static final long SEED = 42;

    public static void main(String[] args) throws InterruptedException, IOException {
        Log.setLevel(ch.qos.logback.classic.Level.ERROR);

        System.out.println("============================================");
        System.out.println("DRL TRAINING SESSION STARTING");
        System.out.println("============================================\n");

        CloudSimPlus simulation = new CloudSimPlus();
        createDatacenter(simulation);
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vmList = createVms();
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(SEED);

        // 1. COLLECT DECISIONS FROM PYTHON
        for (int i = 0; i < CLOUDLETS; i++) {
            long length = 10000 + random.nextInt(40000);
            Cloudlet cloudlet = new CloudletSimple(length, 1);

            // Send State to Python
            writeStateToJson(random.nextDouble(), random.nextDouble(), length);

            // Wait for Action
            int vmIndex = waitForAction();
            if (vmIndex >= VMS) vmIndex = VMS - 1;

            cloudlet.setVm(vmList.get(vmIndex));
            cloudlet.setSubmissionDelay(i * 0.01);
            cloudletList.add(cloudlet);

            if (i % 100 == 0) System.out.printf("[Java] Planning Task %d/%d...\r", i, CLOUDLETS);
        }

        // 2. RUN SIMULATION
        System.out.println("\n[Java] All tasks scheduled. Running simulation engine...");
        broker.submitCloudletList(cloudletList);
        simulation.start();

        // 3. CALCULATE REWARD & SEND TO PYTHON
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        double avgTurnaround = finished.stream()
                .mapToDouble(c -> c.getFinishTime() - c.getSubmissionDelay())
                .average().orElse(1000);

        // REWARD LOGIC:
        // Our Round Robin was ~488s. If the AI is faster than 500s, it gets positive points.
        double reward = 500.0 - avgTurnaround;

        System.out.println("\n" + "=".repeat(60));
        System.out.printf("  SIMULATION COMPLETE | AVG TURNAROUND: %.2f s\n", avgTurnaround);
        System.out.printf("  CALCULATED REWARD FOR AGENT: %.2f\n", reward);
        System.out.println("=".repeat(60));

        writeRewardToJson(reward);
        System.out.println("[Java] Reward sent. Agent is now training...");
    }

    // --- HELPER METHODS ---

    private static void writeStateToJson(double cpu, double ram, long taskSize) {
        String json = String.format("{\"cpu_load\": %.2f, \"ram_load\": %.2f, \"task_size\": %d}", cpu, ram, taskSize);
        try (FileWriter writer = new FileWriter(STATE_FILE)) { writer.write(json); } catch (IOException e) {}
    }

    private static void writeRewardToJson(double reward) {
        String json = String.format("{\"reward\": %.2f}", reward);
        try (FileWriter writer = new FileWriter(REWARD_FILE)) { writer.write(json); } catch (IOException e) {}
    }

    private static int waitForAction() throws InterruptedException, IOException {
        File file = new File(ACTION_FILE);
        while (!file.exists()) { Thread.sleep(5); }
        Thread.sleep(5);
        String content = new String(Files.readAllBytes(Paths.get(ACTION_FILE)));
        String numberOnly = content.replaceAll("[^0-9]", "");
        file.delete();
        return Integer.parseInt(numberOnly);
    }

    private static void createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new PeSimple(10000));
            hostList.add(new HostSimple(32768, 1000000, 1000000, peList));
        }
        new DatacenterSimple(simulation, hostList).getCharacteristics().setCostPerSecond(COST_PER_SEC);
    }

    private static List<Vm> createVms() {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < VMS; i++) {
            Vm vm = new VmSimple(5000, 1);
            vm.setRam(1024).setBw(1000).setSize(10000);
            list.add(vm);
        }
        return list;
    }
}