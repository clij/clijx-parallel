package net.haesleinhuepf.clijx.parallel;

import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clijx.CLIJx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * The CLIJxPool holds instances of CLIJx allowing to execute operations on multiple OpenCL devices / GPUs at a time.
 * <p>
 * A static pool can be acquired with {@link CLIJxPool#getInstance()}.
 * <p>>
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

    // Static shared instance
    static private CLIJxPool INSTANCE = null;

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
        System.out.println("CLIJxPool created:");
        System.out.println(this.getDetails());
    }

    /**
     * @return a static CLIJxPool that will attempt to use all available GPU Devices and create multiple
     * CLIJx instance into a single static shared pool. The pool is initialized on the first call and is then reused,
     * unless {@link CLIJxPool#shutdown()} (or {@link CLIJxPool#forceShutdown()}) is called, which will force the creation
     * of a new pool.
     * <p>
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

    private static CLIJxPool createDefaultPool() {
        int[] devices = CLIJPoolOptions.getDevices();
        int[] threads = CLIJPoolOptions.getThreads();
        return new CLIJxPool(devices, threads);
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
     * @return true if a default static pool instance has already been created, false otherwise
     */
    public static boolean isIntanceSet() {
        return INSTANCE != null;
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
                    .append("\t\t- Img Suport [").append(clijx.hasImageSupport()).append("]  OpenCL [v").append(clijx.getOpenCLVersion()).append("]\n")
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
