package net.haesleinhuepf.clijx.faclonheavy;

import org.junit.Ignore;
import org.junit.Test;

// Looks like these tests are really configuration dependent
public class TestCLIJxPool {
    @Ignore
    @Test
    public void test_pool_from_indices() {
        CLIJxPool pool = new CLIJxPool(new int[]{0}, new int[]{1});
        assert pool.size() == 1;
    }

    @Ignore
    @Test
    public void test_pool_from_device_names() {
        CLIJxPool pool = CLIJxPool.fromDeviceNames(new String[]{"UHD"}, new int[]{1});
        assert pool.size() == 1;
    }

    @Ignore
    @Test
    public void test_pool_from_predefined() {
        CLIJxPool pool = CLIJxPool.fullPool();
        assert pool.size() >0;
    }

}
