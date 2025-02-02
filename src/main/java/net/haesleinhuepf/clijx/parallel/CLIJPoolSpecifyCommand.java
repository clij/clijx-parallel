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
 *
 * TODO: there's an issue if there are multiple identical devices, say 2x 4080 cards. I need to test a configuration like that. Maybe with BIOP Desktop.
 */

@Plugin(type = Command.class, headless = true,
        menuPath = "Plugins>Specify CLIJxPool",
        initializer = "init", // Allows to user to know its configuration
        description = "Allow to specify how GPU resources are split (only applies to tasks using CLIJxPool.getInstance()) ")
public class CLIJPoolSpecifyCommand extends DynamicCommand {

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
    String info_for_user;

    @Parameter(label = "Devices To Exclude (comma separated)")
    String devices_to_exclude_csv = "";

    @Parameter(label = "Split Devices (device name then number, comma separated)", description = "For instance '3080, 8, Intel, 4'")
    String devices_split_csv = "";

    @Override
    public void run() {
        try {
            CLIJ.getAvailableDeviceNames();
        } catch (Exception e) {
            System.out.println("Could not get CL devices information - skipping command.");
            return;
        }

        if (CLIJxPool.isIntanceSet()) {
            IJ.log("A CLIJxPool instance is already set, closing it...");
            CLIJxPool.getInstance().shutdown();
            IJ.log("Previous pool closed.");
        }

        if (!devices_split_csv.trim().isEmpty()) {
            CLIJxPool.clearDevicesSplit();
            String[] devices_to_split = devices_split_csv.trim().split(",");
            if (devices_to_split.length % 2 == 1) {
                System.err.println("You have an issue with the device split parameters, the number or arguments is odd.");
                return;
            }

            // Validate first
            for (int iDevice = 0; iDevice<devices_to_split.length; iDevice+=2) {
                try {
                    int nContexts = Integer.parseInt(devices_to_split[iDevice + 1]);
                    if (nContexts <= 0 ) {
                        System.err.println("Invalid contexts number, a strictly positive number is expected.");
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error while parsing argument "+devices_to_split[iDevice+1]+" - it should be an integer.");
                    return;
                }
            }

            // Do
            for (int iDevice = 0; iDevice<devices_to_split.length; iDevice+=2) {
                String deviceName = devices_to_split[iDevice].trim();
                int nContexts = Integer.parseInt(devices_to_split[iDevice+1]);
                CLIJxPool.setNumberOfInstancePerDevice(deviceName, nContexts);
            }
        }

        if (!devices_to_exclude_csv.trim().isEmpty()) {
            CLIJxPool.clearExcludedDevices();
            String[] devices_to_exclude = devices_to_exclude_csv.trim().split(",");
            for (String device_to_exclude: devices_to_exclude) {
                CLIJxPool.excludeDevice(device_to_exclude);
            }
        }

        IJ.log("- Creating CLIJ Pool:");
        CLIJxPool.getInstance();

        IJ.log("- CLIJ Pool specifications:");
        IJ.log(CLIJxPool.getInstance().getDetails());
    }

    protected void init() { // DO NOT DELETE! This is actually used by the initializer
        ArrayList<String> allDevices = null;
        try {
            allDevices = CLIJ.getAvailableDeviceNames();
        } catch (Exception e) {
            info_for_user = "Could not get CL devices information - this command will not work.";
            return;
        }
        info_for_user = "<html>";
        info_for_user += "<h2>Specify pool of CLIJ instances</h2><br>";
        info_for_user += "Available devices:<br>";
        for (String device: allDevices) {
            info_for_user += "  - "+device+" <br>";
        }
        info_for_user+="</html>";
    }
}


