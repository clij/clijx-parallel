package net.haesleinhuepf.clijx.faclonheavy;

import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clijx.CLIJx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * The CLIJxPool holds instances of CLIJx allowing to execute operations on multiple OpenCL devices / GPUs at a time.
 */
public class CLIJxPool {

    final private ArrayBlockingQueue<CLIJx> pool;
    final private List<CLIJx> allInstances = new ArrayList<>(); // For logging only

    public static final Map<String, Integer> GPU_TO_INSTANCES = new HashMap<>();

    private final static Set<String> EXCLUDED_GPU = new HashSet<>();

    static {
        // "Iris", "UHD", "gfx9", "mx", "1070", "2060", "2070", "2080"
        GPU_TO_INSTANCES.put("Iris", 1); // Intel(R) Iris(R) Xe Graphics
        GPU_TO_INSTANCES.put("UHD", 1);
        GPU_TO_INSTANCES.put("gfx9", 1);
        GPU_TO_INSTANCES.put("mx", 1);

        GPU_TO_INSTANCES.put("A500 Laptop", 1); // NVIDIA RTX A500 Laptop GPU

        GPU_TO_INSTANCES.put("1060", 1);
        GPU_TO_INSTANCES.put("1070", 1);
        GPU_TO_INSTANCES.put("1080", 1);

        GPU_TO_INSTANCES.put("2060", 2);
        GPU_TO_INSTANCES.put("2070", 2);
        GPU_TO_INSTANCES.put("2080", 4);

        GPU_TO_INSTANCES.put("3060", 2);
        GPU_TO_INSTANCES.put("3070", 2);
        GPU_TO_INSTANCES.put("3080", 4);

        GPU_TO_INSTANCES.put("4060", 4);
        GPU_TO_INSTANCES.put("4070", 4);
        GPU_TO_INSTANCES.put("4080", 8);
        GPU_TO_INSTANCES.put("4090", 8);

        GPU_TO_INSTANCES.put("5070", 4);
        GPU_TO_INSTANCES.put("5080", 8);
        GPU_TO_INSTANCES.put("5090", 8);
    }

    public CLIJxPool(int[] device_indices, int[] number_of_instances_per_clij) {
        int sum = 0;
        for (int v : number_of_instances_per_clij) {
            sum = sum + v;
        }
        pool = new ArrayBlockingQueue<>(sum, true);

        for (int i = 0; i < device_indices.length; i++) {
            for (int j = 0; j < number_of_instances_per_clij[i]; j++) {
                CLIJx clijx = new CLIJx(new CLIJ(device_indices[i]));
                pool.add(clijx);
                allInstances.add(clijx);
            }
        }
    }

    public static void excludeDevice(String pDeviceNameMustContain) {
        if (INSTANCE!=null) {
            System.err.println("In order to have an effect, the exclude method should be called BEFORE pool creation ");
        }
        EXCLUDED_GPU.add(pDeviceNameMustContain);
    }

    public static int getNumberOfInstancePerDevice(String pDeviceNameMustContain) {
        Optional<String> gpuType = GPU_TO_INSTANCES.keySet().stream().filter(pDeviceNameMustContain::contains).findFirst();
        if (gpuType.isPresent()) {
            return GPU_TO_INSTANCES.get(gpuType.get());
        } else {
            System.out.println(
                    "Unrecognized CL device "+pDeviceNameMustContain+". A single context will be used for this device. Please use "
                            +CLIJxPool.class.getName()+".setNumberOfInstancePerDevice or use the constructor if you need a different behaviour.");
            return 1;
        }
    }

    public static void setNumberOfInstancePerDevice(String pDeviceNameMustContain, int nInstances) {
        GPU_TO_INSTANCES.put(pDeviceNameMustContain, nInstances);
    }

    private static CLIJxPool createDefaultPool() {
        List<String> allDevices = CLIJ.getAvailableDeviceNames();
        if (allDevices.isEmpty()) {
            throw new RuntimeException("No OpenCL compatible device has been found");
        } else {
            List<Integer> deviceIndex = new ArrayList<>();
            List<Integer> instancesPerDevice = new ArrayList<>();
            for (int iDevice = 0; iDevice < allDevices.size(); iDevice++) {
                String deviceName = allDevices.get(iDevice);
                boolean exclude = EXCLUDED_GPU.stream().anyMatch(deviceName::contains);
                if (exclude) {
                    System.out.println("Excluding device "+deviceName+" from CLIJx pool.");
                    continue;
                }
                deviceIndex.add(iDevice);
                instancesPerDevice.add(getNumberOfInstancePerDevice(deviceName));
            }

            if (deviceIndex.isEmpty()) {
                throw new RuntimeException("With GPU exclusions, no CLIJxPool could be created.");
            }

            assert deviceIndex.size() == instancesPerDevice.size();

            // Stupid Java issue, convert List<Integer> to int[]
            int[] deviceIndexArray = new int[deviceIndex.size()];
            int[] instancePerDeviceArray = new int[instancesPerDevice.size()];
            for (int i = 0; i< deviceIndex.size(); i++) {
                deviceIndexArray[i] = deviceIndex.get(i);
                instancePerDeviceArray[i] = instancesPerDevice.get(i);
            }
            return new CLIJxPool(deviceIndexArray, instancePerDeviceArray);
        }
    }

    public int size() {
        return nInstances();
    }

    public int nInstances() {
        return allInstances.size();
    }

    public int nBusyInstances() {
        return allInstances.size()-pool.size();
    }

    public int nIdleInstances() {
        return pool.size();
    }

    public String getDetails() {
        StringBuilder text = new StringBuilder();
        text.append("CLIJxPool [")
                .append("size:").append(this.allInstances.size()).append(" idle:").append(this.pool.size()).append("]:\n");
        for (CLIJx clijx : allInstances) {
            text.append(pool.contains(clijx) ? "\t- [IDLE] " : "\t- [BUSY] ").append(clijx.getGPUName()).append(" \n")
                    .append("\t\t- ").append(clijx).append("\n");
        }
        if (isShuttingDown) {text.append("SHUTDOWN");}
        return text.toString();
    }

    @Override
    public String toString() {
        return "CLIJxPool [size:" + this.allInstances.size() + " idle:" + this.pool.size() + "]" + ((isShuttingDown?"SHUTDOWN!":""));
    }

    /**
     * Returns a CLIJx instance, immediately if one is available, otherwise waits until one becomes available
     * The CLIJx instance should be recycled when the job is finished
     * @return an available CLIJx instance
     */
    public CLIJx getIdleCLIJx() {
        if (isShuttingDown) {
            throw new RuntimeException("The pool is being shut down.");
        }

        try {
            return pool.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Recycle CLIJx instance, makes it available in the pool again
     * (which should be part of the pool already), empty its memory and mark it as idle.
     * @param clijx the clijx instance to add back into the pool of available clij instances
     */
    public void setCLIJxIdle(CLIJx clijx) {
        if (allInstances.contains(clijx)) {
            pool.add(clijx);
        } else {
            System.err.println("CLIJx "+clijx.getGPUName()+", instance "+clijx+" is not part of the pool "+this);
        }
    }

    private volatile boolean isShuttingDown = false;

    /**
     * Orderly shutdown of the pool, closes all CLIJx context until no one is left in the pool
     */
    public void shutdown() {
        synchronized (this) {
            if (isShuttingDown) return; // It's already shutting down in another thread
            isShuttingDown = true;
        }

        int nInstancesLeft = allInstances.size();

        while (nInstancesLeft>0) {
            try {
                CLIJx clijx = pool.take();
                clijx.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            nInstancesLeft--;
        }

        allInstances.clear();

        if ((INSTANCE == this)) {
            INSTANCE = null;
        }
    }

    public void forceShutdown() {
        isShuttingDown = true;
        pool.clear();
        allInstances.forEach( clij ->
            new Thread(clij::close).start()
        );
        allInstances.clear();

        if ((INSTANCE == this)) {
            INSTANCE = null;
        }
    }

    // Static methods to set a default pool that can be re-used at the JVM scale
    // Kept public in order to be able to set it easily
    private static CLIJxPool INSTANCE = null;

    static synchronized CLIJxPool getInstance() {
        if (INSTANCE != null) {
            if (INSTANCE.isShuttingDown) {
                System.err.println("CLIJxPool instance is shutting down!");
            }
            return INSTANCE;
        }
        // Pool not created
        INSTANCE = createDefaultPool();
        return INSTANCE;
    }

    static synchronized void setInstance(CLIJxPool pool) {
        if (INSTANCE!=null) {
            System.err.println("Pre-existing CLIJxPool instance overridden!");
        }
        INSTANCE = pool;
    }

}
