package net.haesleinhuepf.clijx.parallel;

import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clijx.CLIJx;
import org.junit.Test;
import org.junit.Assert;

/**
 * These tests are really configuration dependent, if you have an idea in order to make them clean, please contribute!
 * <p>
 * While waiting for a good method, you can set the static fields at the beginning of the tests in order to adapt
 * the configuration and test on your own device.
 */
public class TestCLIJxPool {

    // Change this flag to ignore or test all tests
    public static boolean IGNORE_TESTS = false;

    @Test
    public void testConfig() {
        if (IGNORE_TESTS) return;
        int nCLIJDevices = CLIJ.getAvailableDeviceNames().size();
        Assert.assertTrue("You do not have any OpenCL compatible devices.", nCLIJDevices>0);
    }

    @Test
    public void testGetSetIdleCLIJx() {
        if (IGNORE_TESTS) return;

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

    @Test
    public void testShutdown() {
        if (IGNORE_TESTS) return;
        CLIJxPool INSTANCE = CLIJxPool.getInstance();

        INSTANCE.shutdown();

        Assert.assertEquals("Pool still has instances", 0 , INSTANCE.nInstances());
        Assert.assertEquals("Pool still has busy instances", 0 , INSTANCE.nBusyInstances());
        Assert.assertEquals("Pool still has idle instances", 0 , INSTANCE.nIdleInstances());
    }

    @Test
    public void testForceShutdown() {
        if (IGNORE_TESTS) return;
        CLIJxPool INSTANCE = CLIJxPool.getInstance();

        // Gets a CLIJx instance
        INSTANCE.getIdleCLIJx();

        INSTANCE.forceShutdown();

        Assert.assertEquals("Pool still has instances", 0 , INSTANCE.nInstances());
        Assert.assertEquals("Pool still has busy instances", 0 , INSTANCE.nBusyInstances());
        Assert.assertEquals("Pool still has idle instances", 0 , INSTANCE.nIdleInstances());
    }


    @Test
    public void testSetInstance() {
        if (IGNORE_TESTS) return;
        CLIJxPool pool1 = CLIJxPool.getInstance();
        CLIJxPool pool2 = new CLIJxPool(new int[]{0}, new int[]{1});

        Assert.assertEquals("Unexpected default pool instance", pool1, CLIJxPool.getInstance());

        CLIJxPool.setInstance(pool2);
        Assert.assertEquals("setInstance does not override the default pool instance", pool2, CLIJxPool.getInstance());

        pool1.shutdown();
        pool2.shutdown();
    }

}
