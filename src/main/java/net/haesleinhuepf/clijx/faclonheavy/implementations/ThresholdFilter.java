package net.haesleinhuepf.clijx.faclonheavy.implementations;

import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clijx.faclonheavy.AbstractTileProcessor;

import java.util.Arrays;

/**
 * An example implementation of an image processing workflow that can be executed on tiles of images.
 *
 * @author: Robert Haase
 */
public class ThresholdFilter extends AbstractTileProcessor {

    @Override
    public void accept(ClearCLBuffer input, ClearCLBuffer output) {
        System.out.println("Start processing on " + clijx.getGPUName() + " image dimensions " + Arrays.toString(input.getDimensions()));
        long start_time = System.nanoTime();

        // 3d gaussian blur
        ClearCLBuffer tmpGaussian = clijx.create(input);
        float sigmaX = 5.0f;
        float sigmaY = 5.0f;
        float sigmaZ = 5.0f;
        clijx.gaussianBlur3D(input, tmpGaussian, sigmaX, sigmaY, sigmaZ);

        // mean threshold
        ClearCLBuffer tmpThreshold = clijx.create(tmpGaussian);
        clijx.thresholdMean(tmpGaussian, tmpThreshold);
        tmpGaussian.close();

        // masked voronoi labelling
        clijx.maskedVoronoiLabeling(input, tmpThreshold, output);
        tmpThreshold.close();

        long duration_ms = (System.nanoTime() - start_time) / 1000000;
        System.out.println("Finished processing on " + clijx.getGPUName() + " after " + duration_ms + " ms");
    }
}
