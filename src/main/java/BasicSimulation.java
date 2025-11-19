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
import java.util.List;

public class BasicSimulation {

    private static final int HOSTS = 2;
    private static final int VMS = 5;
    private static final int CLOUDLETS = 10;

    public static void main(String[] args) {
        CloudSimPlus simulation = new CloudSimPlus();

        createDatacenter(simulation);
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // Create VMs
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < VMS; i++) {
            long mips = 1000 + (i * 100);
            Vm vm = new VmSimple(mips, 1);
            vm.setRam(512).setBw(1000).setSize(10000);
            vmList.add(vm);
        }
        broker.submitVmList(vmList);

        // Create Cloudlets
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < CLOUDLETS; i++) {
            long length = 10000 + (i * 500);
            Cloudlet cloudlet = new CloudletSimple(length, 1);
            cloudlet.setFileSize(300);
            cloudlet.setOutputSize(300);
            cloudletList.add(cloudlet);
        }
        broker.submitCloudletList(cloudletList);

        // ================================================================
        // NEW: Add a Listener to watch the simulation WHILE it runs
        // ================================================================
        simulation.addOnClockTickListener(info -> {
            int time = (int) info.getTime();
            // Print status every 10 seconds (and only if time > 0)
            if (time > 0 && time % 10 == 0) {
                System.out.println("\n[Time: " + time + "s] Checking System Health...");
                for (Vm vm : vmList) {
                    // Get CPU Usage (0.0 to 1.0)
                    double cpu = vm.getCpuPercentUtilization();
                    if(cpu > 0) {
                        System.out.printf("   -> VM %d is BUSY (CPU: %.1f%%)\n", vm.getId(), cpu * 100);
                    }
                }
            }
        });
        // ================================================================

        simulation.start();

        new org.cloudsimplus.builders.tables.CloudletsTableBuilder(broker.getCloudletFinishedList())
                .build();
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                peList.add(new PeSimple(10000)); // High speed CPUs
            }
            Host host = new HostSimple(8192, 100000, 1000000, peList);
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }
}