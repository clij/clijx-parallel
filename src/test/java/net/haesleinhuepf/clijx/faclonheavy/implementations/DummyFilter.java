package net.haesleinhuepf.clijx.faclonheavy.implementations;

import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clijx.faclonheavy.AbstractTileProcessor;

import java.util.Arrays;

/**
 * An example implementation of an image processing workflow that can be executed on tiles of images.
 *
 * @author: Robert Haase
 */
public class DummyFilter extends AbstractTileProcessor {

    @Override
    public void accept(ClearCLBuffer input, ClearCLBuffer output) {
        System.out.println("Start processing on " + clijx.getGPUName() + " image dimensions " + Arrays.toString(input.getDimensions()));
        long start_time = System.nanoTime();

        // allocated temporary memory
        ClearCLBuffer temp = clijx.create(input);

        // process the image
        clijx.differenceOfGaussian(input, temp, 1, 2, 3, 4, 5, 6);
        clijx.thresholdOtsu(temp, output);

        // clean up
        temp.close();
        long duration_ms = (System.nanoTime() - start_time) / 1000000;
        System.out.println("Finished processing on " + clijx.getGPUName() + " after " + duration_ms + " ms");
    }
}
