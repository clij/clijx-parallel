package net.haesleinhuepf.clijx.parallel;

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
 * <p>
 * A static pool that will attempt to use all devices can be acquired with {@link CLIJxPool#getInstance()}.
 * Before the first call of {@link CLIJxPool#getInstance()}, specific devices can be excluded from the pool
 * by calling via {@link CLIJxPool#excludeDevice(String)}, and the way they are split into multiple contexts can be
 * modified via {@link CLIJxPool#setNumberOfInstancePerDevice(String, int)}
 * <p>
 * NOTE: the default number of threads per type of GPU is stored in a default static map that can be modified
 * via pull request, please do not hesitate to open one rather than using {@link CLIJxPool#setNumberOfInstancePerDevice(String, int)}.
 * <p>
 * It is also possible to directly create a pool with the constructor {@link CLIJxPool#CLIJxPool(int[], int[])}
 * Such an instance can be set as the static shared pool by calling {@link CLIJxPool#setInstance(CLIJxPool)}.
 * <p>
 * To use a pool, you can acquire a CLIJx instance from the pool by calling {@link CLIJxPool#getIdleCLIJx()} and, after
 * a task is done with a particular CLIJx instance, to recycle (put back into the pool) the instance by
 * calling {@link CLIJxPool#setCLIJxIdle(CLIJx)}. This device does not touch the memory state of the CLIJx instances.
 * It is thus also the responsibility of the caller to avoid memory leaks.
 */
public class CLIJxPool {

    final private ArrayBlockingQueue<CLIJx> idleInstances; // The core of the pooling mechanism
    final private List<CLIJx> allInstances = new ArrayList<>(); // Stores all instances which are part of this pool
    private volatile boolean isShuttingDown = false; // Flags whether the pool is currently being shut down (stays true when shutting down is finished)

    // Below: static objects which allow to tune the creation of the static shared GPU pool
    static private final Map<String, Integer> GPU_TO_INSTANCES = new HashMap<>();
    static private final Set<String> EXCLUDED_GPU = new HashSet<>();

    // Static shared instance
    static private CLIJxPool INSTANCE = null;

    // Default choices for splitting GPUs into several threads / context, can be modified via {@link CLIJxPool#setNumberOfInstancePerDevice(String, int)}
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

    /**
     * Unless you know what you are doing, please use {@link CLIJxPool#getInstance()} instead of this constructor
     * @param deviceIndices array indicating the device indices
     * @param numberOfInstancesPerCLIJ specifies how many CLIJx instances are created per GPU device
     */
    public CLIJxPool(int[] deviceIndices, int[] numberOfInstancesPerCLIJ) {
        int sum = 0;
        for (int v : numberOfInstancesPerCLIJ) {
            sum = sum + v;
        }

        idleInstances = new ArrayBlockingQueue<>(sum, true);

        for (int i = 0; i < deviceIndices.length; i++) {
            for (int j = 0; j < numberOfInstancesPerCLIJ[i]; j++) {
                CLIJx clijx = new CLIJx(new CLIJ(deviceIndices[i]));
                idleInstances.add(clijx);
                allInstances.add(clijx);
            }
        }
    }

    /**
     * @return a static CLIJxPool that will attempt to use all available GPU Devices and create multiple
     * CLIJx instance into a single static shared pool. The pool is initialized on the first call and is then reused,
     * unless {@link CLIJxPool#shutdown()} (or {@link CLIJxPool#forceShutdown()}) is called, which will force the creation
     * of a new pool.
     *
     * If you are not happy with the default pool, you can specify some options before creation with:
     * Method 1: the static configuration methods
     * {@link CLIJxPool#excludeDevice(String)} and {@link CLIJxPool#setNumberOfInstancePerDevice(String, int)}
     *
     * Method 2: the CLIJxPool construction, and if needed, {@link CLIJxPool#setInstance(CLIJxPool)} will set the
     * static CLIJxPool instance which is returned from this method
     */
    public static synchronized CLIJxPool getInstance() {
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

    /**
     * see {@link CLIJxPool#getInstance()}
     * @param pool is the CLIJxPool instance that will be returned by calls of {@link CLIJxPool#getInstance()}
     */
    public static synchronized void setInstance(CLIJxPool pool) {
        if (INSTANCE!=null) {
            System.err.println("Pre-existing CLIJxPool instance overridden!");
        }
        INSTANCE = pool;
    }

    /**
     * CLIJxPool static instance configuration:
     * When called BEFORE {@link CLIJxPool#getInstance()}, the GPU device specified in this method will not be part
     * of the CLIJxPool.
     * @param pDeviceNameMustContain a part of the GPU name
     */
    public static void excludeDevice(String pDeviceNameMustContain) {
        if (INSTANCE!=null) {
            System.err.println("In order to have an effect, the exclude method should be called BEFORE pool creation ");
        }
        EXCLUDED_GPU.add(pDeviceNameMustContain);
    }

    /**
     * CLIJxPool static instance configuration:
     * When called BEFORE {@link CLIJxPool#getInstance()}, allow to specify in how many CLIJx instances a device from the pool should be split
     * @param pDeviceNameMustContain a part of the GPU name
     * @param nInstances the number of threads to CLIJx instance to create on the specified device
     */
    public static void setNumberOfInstancePerDevice(String pDeviceNameMustContain, int nInstances) {
        GPU_TO_INSTANCES.put(pDeviceNameMustContain, nInstances);
    }

    /**
     * Returns the number of CLIJx instances that will be created in the default static CLIJx pool on a particular device
     * @param pDeviceNameMustContain a part of the GPU name
     * @return number of CLIJx instances planned to be created on the first call of {@link CLIJxPool#getInstance()}
     */
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

    /**
     * @return total number of CLIJx instances (busy or not) contained in the pool, identical to {@link CLIJxPool#nInstances()}
     */
    public int size() {
        return nInstances();
    }

    /**
     * @return total number of CLIJx instances (busy or not) contained in the pool, identical to {@link CLIJxPool#size()}
     */
    public int nInstances() {
        return allInstances.size();
    }

    /**
     * @return number of busy CLIJx instances contained in the pool
     */
    public int nBusyInstances() {
        return allInstances.size()- idleInstances.size();
    }

    /**
     * @return number of idle CLIJx instances contained in the pool
     */
    public int nIdleInstances() {
        return idleInstances.size();
    }

    /**
     * @return a String representation of all CLIJx instances of the pool with their identifier and status (idle, busy)
     */
    public String getDetails() {
        StringBuilder text = new StringBuilder();
        text.append("CLIJxPool [")
                .append("size:").append(this.allInstances.size()).append(" idle:").append(this.idleInstances.size()).append("]:\n");
        for (CLIJx clijx : allInstances) {
            text.append(idleInstances.contains(clijx) ? "\t- [IDLE] " : "\t- [BUSY] ").append(clijx.getGPUName()).append(" \n")
                    .append("\t\t- ").append(clijx).append("\n");
        }
        if (isShuttingDown) {text.append("SHUTDOWN");}
        return text.toString();
    }

    @Override
    public String toString() {
        return "CLIJxPool [size:" + this.allInstances.size() + " idle:" + this.idleInstances.size() + "]" + ((isShuttingDown?"SHUTDOWN!":""));
    }

    /**
     * @return an idle CLIJx instance from the pool, immediately if one is available from the pool OR
     * blocks until one becomes available.
     * <p>
     * The returned CLIJx instance should be returned to the pool once its job is done with {@link CLIJxPool#setCLIJxIdle(CLIJx)}
     */
    public CLIJx getIdleCLIJx() {
        if (isShuttingDown) {
            throw new RuntimeException("The CLIJxPool is being shut down, can't get any instance!");
        }
        try {
            return idleInstances.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Put back a CLIJx instance into the queue of idle instances
     * @param clijx the clijx instance to recycle
     */
    public synchronized void setCLIJxIdle(CLIJx clijx) {
        if (allInstances.contains(clijx)) {
            if (idleInstances.contains(clijx)) {
                System.err.println("CLIJx "+clijx.getGPUName()+", instance "+clijx+" has already been recycled!");
                return;
            }
            idleInstances.add(clijx);
        } else {
            System.err.println("CLIJx "+clijx.getGPUName()+", instance "+clijx+" is not part of the pool "+this);
        }
    }

    /**
     * Orderly shutdown of the CLIJxPool, closes all CLIJx context until no one is left in the pool
     */
    public void shutdown() {
        synchronized (this) {
            if (isShuttingDown) return; // It's already shutting down in another thread
            isShuttingDown = true;
        }

        int nInstancesLeft = allInstances.size();

        while (nInstancesLeft>0) {
            try {
                CLIJx clijx = idleInstances.take();
                clijx.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            nInstancesLeft--;
        }

        allInstances.clear();

        if ((INSTANCE == this)) { // Avoid static leak, and allow to call back getInstance without error messages if necessary
            INSTANCE = null;
        }
    }

    /**
     * Forces shutdown of the CLIJxPool, even if some CLIJx instance are still busy.
     * May be useful, use at your own risk!
     */
    public void forceShutdown() {
        isShuttingDown = true;
        idleInstances.clear();
        allInstances.forEach( clij ->
            new Thread(clij::close).start()
        );
        allInstances.clear();

        if ((INSTANCE == this)) {
            INSTANCE = null;
        }
    }

}
