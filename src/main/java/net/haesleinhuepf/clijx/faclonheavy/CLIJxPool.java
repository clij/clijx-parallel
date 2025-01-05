package net.haesleinhuepf.clijx.faclonheavy;

import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clijx.CLIJx;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * The CLIJxPool holds instances of CLIJx allowing to execute operations on multiple OpenCL devices / GPUs at a time.
 */
public class CLIJxPool {

    final ArrayBlockingQueue<CLIJx> pool;
    final int size;
    final CLIJx[] allInstances; // For logging only

    public CLIJxPool(int[] device_indices, int[] number_of_instances_per_clij) {
        int sum = 0;
        for (int v : number_of_instances_per_clij) {
            sum = sum + v;
        }
        size = sum;
        pool = new ArrayBlockingQueue<>(size, true);
        allInstances = new CLIJx[size];

        int count = 0;
        for (int i = 0; i < device_indices.length; i++) {
            for (int j = 0; j < number_of_instances_per_clij[i]; j++) {
                CLIJx clijx = new CLIJx(new CLIJ(device_indices[i]));
                pool.add(clijx);
                allInstances[count] = clijx;
                count++;
            }
        }
    }

    public static CLIJxPool fromDeviceNames(String[] device_names, int[] number_of_instances_per_clij) {
        ArrayList<Integer> index_list = new ArrayList();
        ArrayList<Integer> instance_count_list = new ArrayList();

        int index = 0;
        for (String name : CLIJ.getAvailableDeviceNames()) {

            for (int i = 0; i < device_names.length; i++) {
                String search_string = device_names[i];
                if (name.contains(search_string)) {
                    index_list.add(index);
                    instance_count_list.add(number_of_instances_per_clij[i]);
                }
            }
            index++;
        }

        int[] indices = new int[index_list.size()];
        int[] instance_counts = new int[index_list.size()];

        for (int i = 0; i < index_list.size(); i++) {
            indices[i] = index_list.get(i);
            instance_counts[i] = instance_count_list.get(i);
        }

        return new CLIJxPool(indices, instance_counts);
    }

    public static CLIJxPool fullPool() {
        return CLIJxPool.fromDeviceNames(
                new String[]{"UHD", "gfx9", "mx", "1070", "2060", "2070", "2080"},
                new int[]   {     1,     1,     1,     1,      2,      2,      4}
        );
    }

    public static CLIJxPool powerPool() {
        return CLIJxPool.fromDeviceNames(
                new String[]{"1070", "2060", "2070", "2080"},
                new int[]   {     1,      2,      2,      4}
        );
    }

    public int size() {
        return size;
    }

    public String log() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < size; i ++ ) {
            text.append(" * " + allInstances[i].getGPUName() + "[" + allInstances[i] + "]" + "\n");
        }
        return text.toString();
    }

    /**
     * Select a CLIJx instance that's idle at the moment, mark it as busy and return it.
     * @return a clijx instance
     */
    public CLIJx getIdleCLIJx() {
        // Catch the exception inside to avoid breaking change. Is it a good idea ?
        try {
            return pool.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieve a CLIJx instance (which should be part of the pool already), empty its memory and mark it as idle.
     * 
     * @param clijx
     */
    public void setCLIJxIdle(CLIJx clijx, boolean clear) {
        // clean up that instance before another thread can use it.
        if (clear) clijx.clear();
        pool.add(clijx);
    }

    public void setCLIJxIdle(CLIJx clijx) {
        setCLIJxIdle(clijx, true);
    }
}
