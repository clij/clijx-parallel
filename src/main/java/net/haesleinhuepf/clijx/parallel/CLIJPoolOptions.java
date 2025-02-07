package net.haesleinhuepf.clijx.parallel;

import ij.IJ;
import net.haesleinhuepf.clij.CLIJ;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;

/**
 * Allow to specify how the default static {@link CLIJxPool} is created
 * One can exclude some devices from the pool, or fine tune some devices by setting
 * how many contexts should be created for each available CL device
 */

@Plugin(type = Command.class, headless = true,
        menuPath = "Edit>Options>CLIJ Pool Options",
        initializer = "init", // Allows to user to know its configuration
        description = "Specify how GPU resources are used in the default CLIJxPool instance")
public class CLIJPoolOptions extends DynamicCommand {

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
    String info_for_user;

    @Parameter(label = "Devices To Exclude (comma separated)")
    String poolSpecification = "0:1";

    final public static String KEY = CLIJPoolOptions.class.getName()+".poolSpecification";

    public static int[] getDevices() {
        String prefSpecs = ij.Prefs.get(KEY,"0:1");
        return parseDeviceThreads(prefSpecs)[0];
    }

    public static int[] getThreads() {
        String prefSpecs = ij.Prefs.get(KEY,"0:1");
        return parseDeviceThreads(prefSpecs)[1];
    }

    public static void set(String specs) {
        ij.Prefs.set(KEY, specs);
    }

    @Override
    public void run() {

        // Checks whether there's at least one OpenCL Device available
        try {
            CLIJ.getAvailableDeviceNames();
        } catch (Exception e) {
            System.out.println("Could not get CL devices information - skipping command.");
            return;
        }

        // First - checks validity of pool specifications
        int[][] specs = parseDeviceThreads(poolSpecification); // Write errors if some exist, but does not create the pool
        int[] devices = specs[0];
        int nDevices = CLIJ.getAvailableDeviceNames().size();
        for (int iDevice : devices) {
            if ((iDevice>=nDevices)||(iDevice<0)) {
                System.out.println("CLIJ Pool Option ERROR: the device "+iDevice+" does not exist.");
                return;
            }
        }

        // Second - store specification in prefs
        ij.Prefs.set(CLIJPoolOptions.class.getName()+".poolSpecification", poolSpecification);

        // Third - creates pool, and take care of closing the previous one
        if (CLIJxPool.isIntanceSet()) {
            IJ.log("A CLIJxPool instance is already set, closing it...");
            CLIJxPool.getInstance().shutdown();
            IJ.log("Previous pool closed.");
        }

        IJ.log("- Creating CLIJ Pool:");
        CLIJxPool.getInstance();

        IJ.log("- CLIJ Pool specifications:");
        IJ.log(CLIJxPool.getInstance().getDetails());
    }

    protected void init() { // DO NOT DELETE! This is actually used by the initializer
        ArrayList<String> allDevices;
        try {
            allDevices = CLIJ.getAvailableDeviceNames();
            System.out.println(CLIJ.clinfo());
        } catch (Exception e) {
            info_for_user = "Could not get CL devices information - this command will not work.";
            return;
        }
        info_for_user = "<html>";
        info_for_user += "<h2>Specify pool of CLIJ instances</h2><br>";
        info_for_user += "Available devices:<br>";
        for (int i = 0; i<allDevices.size(); i++){
            String device = allDevices.get(i);
            info_for_user += "  - ["+i+"] "+device+" <br>";
        }
        info_for_user+="<br>Specify <b>device_index:threads_number</b> for each device, comma separated<br>";
        info_for_user+="For instance, to use 2 contexts for the device 0 and 1 for the device 1, type:<br><br>";
        info_for_user+="0:2, 1:1";
        info_for_user+="</html>";
    }


    public static int[][] parseDeviceThreads(String input) {
        String[] pairs = input.split(",");
        ArrayList<Integer> deviceIndices = new ArrayList<>();
        ArrayList<Integer> threadCounts = new ArrayList<>();

        for (String pair : pairs) {
            String[] parts = pair.trim().split(":");
            if (parts.length == 2) {
                try {
                    int deviceIndex = Integer.parseInt(parts[0].trim());
                    int threadCount = Integer.parseInt(parts[1].trim());
                    deviceIndices.add(deviceIndex);
                    threadCounts.add(threadCount);
                } catch (NumberFormatException e) {
                    System.out.println("CLIJ Pool Option ERROR: Invalid format in pair: " + pair);
                }
            } else {
                System.out.println("CLIJ Pool Option ERROR: Invalid pair format: " + pair);
            }
        }

        // Convert ArrayLists to int arrays
        int[] devices = deviceIndices.stream().mapToInt(i -> i).toArray();
        int[] threads = threadCounts.stream().mapToInt(i -> i).toArray();

        return new int[][]{devices, threads};
    }
}