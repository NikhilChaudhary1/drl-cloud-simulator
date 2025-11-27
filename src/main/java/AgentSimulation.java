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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Bridge Simulation
 * This class pauses for every Cloudlet, asks Python for a decision,
 * and then assigns the task to that VM.
 */
public class AgentSimulation {

    // Must match Python config
    private static final String STATE_FILE = "state.json";
    private static final String ACTION_FILE = "action.json";
    private static final int HOSTS = 5;
    private static final int VMS = 5;       // Must match ACTION_DIM in Python
    private static final int CLOUDLETS = 20; // Number of tasks to simulate

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("============================================");
        System.out.println("STARTING AGENT-DRIVEN SIMULATION");
        System.out.println("Make sure 'drl_agent.py' is running!");
        System.out.println("============================================");

        // 1. Init Simulation
        CloudSimPlus simulation = new CloudSimPlus();
        createDatacenter(simulation);
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // 2. Create VMs and submit them
        List<Vm> vmList = createVms();
        broker.submitVmList(vmList);

        // 3. THE INTERACTIVE LOOP
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(42);

        for (int i = 0; i < CLOUDLETS; i++) {
            // Create a random task
            long length = 1000 + random.nextInt(9000);
            Cloudlet cloudlet = new CloudletSimple(length, 1);

            System.out.printf("\n[Java] Task #%d Created (Size: %d MI). Asking Python...\n", i, length);

            // --- STEP A: EXPORT STATE TO JSON ---
            // We mock CPU/RAM load for now (random numbers 0.0 to 1.0)
            double cpuLoad = random.nextDouble();
            double ramLoad = random.nextDouble();
            writeStateToJson(cpuLoad, ramLoad, length);

            // --- STEP B: WAIT FOR PYTHON RESPONSE ---
            int vmIndex = waitForAction();

            // --- STEP C: EXECUTE DECISION ---
            Vm selectedVm = vmList.get(vmIndex);
            System.out.printf("[Java] Python selected VM #%d (ID: %d)\n", vmIndex, selectedVm.getId());

            // Bind the Cloudlet to that specific VM
            cloudlet.setVm(selectedVm);
            cloudletList.add(cloudlet);
        }

        // 4. Run the simulation
        System.out.println("\n--------------------------------------------");
        System.out.println("[Java] All decisions made. Running Simulation...");
        broker.submitCloudletList(cloudletList);
        simulation.start();

        // 5. Results
        System.out.println("--------------------------------------------");
        System.out.println("SIMULATION COMPLETE");
        // Print simple results
        new org.cloudsimplus.builders.tables.CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
    }

    // --- HELPER: WRITE STATE ---
    private static void writeStateToJson(double cpu, double ram, long taskSize) {
        // Create JSON string manually to avoid import issues
        String json = String.format("{\"cpu_load\": %.2f, \"ram_load\": %.2f, \"task_size\": %d}", cpu, ram, taskSize);

        try (FileWriter writer = new FileWriter(STATE_FILE)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- HELPER: WAIT FOR ACTION ---
    private static int waitForAction() throws InterruptedException, IOException {
        File file = new File(ACTION_FILE);

        // Wait until file exists
        while (!file.exists()) {
            Thread.sleep(50); // check every 50ms
        }

        // Wait a tiny bit more to ensure Python finished writing
        Thread.sleep(50);

        // Read the file
        String content = new String(Files.readAllBytes(Paths.get(ACTION_FILE)));

        // Parse simple JSON: {"vm_id": 3}
        // We just extract the numbers using regex to be safe/lazy
        String numberOnly = content.replaceAll("[^0-9]", "");
        int vmId = Integer.parseInt(numberOnly);

        // Delete the action file so we don't read it twice
        file.delete();

        return vmId;
    }

    // --- SETUP HELPERS (Standard CloudSim) ---
    private static void createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new PeSimple(10000)); // 10k MIPS
            hostList.add(new HostSimple(50000, 100000, 100000, peList));
        }
        new DatacenterSimple(simulation, hostList);
    }

    private static List<Vm> createVms() {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < VMS; i++) {
            Vm vm = new VmSimple(1000 + (i * 500), 1); // Different speeds
            list.add(vm);
        }
        return list;
    }
}