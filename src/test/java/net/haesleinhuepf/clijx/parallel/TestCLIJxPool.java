package net.haesleinhuepf.clijx.parallel;

import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clijx.CLIJx;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.Assert;

/**
 * These tests are ignored if no OpenCL device is present
 */
public class TestCLIJxPool {

    public boolean ignoreTests() {
        return CLIJ.getAvailableDeviceNames().isEmpty();
    }

    @Ignore
    @Test
    public void testGetSetIdleCLIJx() {
        if (ignoreTests()) return;

        CLIJxPool INSTANCE = CLIJxPool.getInstance();

        System.out.println("Initial state:");
        System.out.println(INSTANCE.getDetails());

        System.out.println("Acquiring a CLIJ instance:");
        int nIdleInstances = INSTANCE.nIdleInstances();
        int nBusyInstances = INSTANCE.nBusyInstances();

        CLIJx clijx = INSTANCE.getIdleCLIJx();
        Assert.assertEquals("An instance of CLIJx do not seem to be acquired from the pool or nIdleInstances does not behave correctly",
                INSTANCE.nIdleInstances(), nIdleInstances - 1);

        Assert.assertEquals("An instance of CLIJx do not seem to be acquired from the pool or nBusyInstances does not behave correctly",
                INSTANCE.nBusyInstances(), nBusyInstances + 1);

        System.out.println(INSTANCE.getDetails());

        System.out.println("Releasing it:");
        INSTANCE.setCLIJxIdle(clijx);
        Assert.assertEquals("An instance of CLIJx do not seem to be recycled into the pool",
                INSTANCE.nIdleInstances(), nIdleInstances);
        System.out.println(INSTANCE.getDetails());

        INSTANCE.shutdown();
    }

    @Ignore
    @Test
    public void testShutdown() {
        if (ignoreTests()) return;
        CLIJxPool INSTANCE = CLIJxPool.getInstance();

        INSTANCE.shutdown();

        Assert.assertEquals("Pool still has instances", 0 , INSTANCE.nInstances());
        Assert.assertEquals("Pool still has busy instances", 0 , INSTANCE.nBusyInstances());
        Assert.assertEquals("Pool still has idle instances", 0 , INSTANCE.nIdleInstances());
    }

    @Ignore
    @Test
    public void testForceShutdown() {
        if (ignoreTests()) return;
        CLIJxPool INSTANCE = CLIJxPool.getInstance();

        // Gets a CLIJx instance
        INSTANCE.getIdleCLIJx();

        INSTANCE.forceShutdown();

        Assert.assertEquals("Pool still has instances", 0 , INSTANCE.nInstances());
        Assert.assertEquals("Pool still has busy instances", 0 , INSTANCE.nBusyInstances());
        Assert.assertEquals("Pool still has idle instances", 0 , INSTANCE.nIdleInstances());
    }

    @Ignore
    @Test
    public void testSetInstance() {
        if (ignoreTests()) return;
        CLIJxPool pool1 = CLIJxPool.getInstance();
        CLIJxPool pool2 = new CLIJxPool(new int[]{0}, new int[]{1}); // Device 0 with one thread

        Assert.assertEquals("Unexpected default pool instance", pool1, CLIJxPool.getInstance());

        CLIJxPool.setInstance(pool2);
        Assert.assertEquals("setInstance does not override the default pool instance", pool2, CLIJxPool.getInstance());

        pool1.shutdown();
        pool2.shutdown();
    }

    @Ignore
    @Test
    public void testExcludeDevice() {
        if (ignoreTests()) return;
        CLIJxPool pool = CLIJxPool.getInstance();
        int nInstances = pool.nInstances();
        pool.shutdown();
        CLIJxPool.excludeDevice(CLIJ.getAvailableDeviceNames().get(0));
        pool = CLIJxPool.getInstance();
        Assert.assertTrue("excludeDevice is not excluding any device", pool.nInstances()<nInstances);
        pool.shutdown();
    }

}
