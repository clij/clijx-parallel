package net.haesleinhuepf.clijx.faclonheavy;

import net.haesleinhuepf.clij.CLIJ;
import org.junit.Assert;
import org.junit.Test;

/**
 * These tests are really configuration dependent, if you have an idea in order to make them clean, please contribute!
 * <p>
 * While waiting for a good method, you can set the static fields at the beginning of the tests in order to adapt
 * the configuration and test on your own device.
 */
public class TestCLIJxMultiDevicePool {

    // Change this flag to ignore or test all tests
    public static boolean IGNORE_TESTS = false;

    @Test
    public void testConfig() {
        if (IGNORE_TESTS) return;
        int nCLIJDevices = CLIJ.getAvailableDeviceNames().size();
        Assert.assertTrue("Not enough OpenCL devices", nCLIJDevices>1);
    }

    @Test
    public void testExcludeDevice() {
        if (IGNORE_TESTS) return;
        CLIJxPool pool = CLIJxPool.getInstance();
        int nInstances = pool.nInstances();
        pool.shutdown();
        CLIJxPool.excludeDevice(CLIJ.getAvailableDeviceNames().get(0));
        pool = CLIJxPool.getInstance();
        Assert.assertTrue("excludeDevice is not excluding any device", pool.nInstances()<nInstances);
        pool.shutdown();
    }

}
