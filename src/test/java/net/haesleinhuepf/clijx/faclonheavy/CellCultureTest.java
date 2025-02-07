package net.haesleinhuepf.clijx.faclonheavy;

import java.io.IOException;

import net.haesleinhuepf.clijx.faclonheavy.implementations.ThresholdFilter;

import org.janelia.saalfeldlab.i2k2020.ops.CLIJxFilterOp;
import org.janelia.saalfeldlab.i2k2020.util.Lazy;
import org.janelia.saalfeldlab.i2k2020.util.N5Factory;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Process an image tile-by-tile on multiple GPUs
 *
 * This code originates from https://github.com/saalfeldlab/i2k2020-imglib2-advanced/blob/main/src/main/java/org/janelia/saalfeldlab/i2k2020/LazyTutorial3.java
 *
 * @author Stephan Saalfeld, Robert Haase
 */
public class CellCultureTest {

    public static final void main(final String... args) throws IOException {

        // define location of n5 dataset
        final N5Reader n5 = N5Factory.openReader("/Users/rossg/Documents/clij-data/n5/cell_culture_crop.n5/data");
        final RandomAccessibleInterval<FloatType> img = N5Utils.openVolatile(n5, "c3/s0");
        // convert the input image from any type to FloatType
        final RandomAccessibleInterval<FloatType> floats = Converters.convert(img, (a, b) -> b.set(a.getRealFloat()), new FloatType());

        // create a pool of clijx instances representing OpenCL contexts executed on different GPUs
        // specify GPU name and number of threads
        CLIJxPool pool = CLIJxPool.fromDeviceNames(
                new String[]{"Intel(R) Iris(TM)"},
                new int[]{4});
        System.out.println("CLIJ pool size: " + pool.size());
        System.out.println("CLIJ pool: \n" + pool.log());

        // create a processor representing the workflow we want to apply to the image
        final CLIJxFilterOp<FloatType, FloatType> clijxFilter =
                new CLIJxFilterOp<FloatType, FloatType>(Views.extendMirrorSingle(floats), pool, ThresholdFilter.class, 100, 100, 100);

        final RandomAccessibleInterval<FloatType> filtered = Lazy.generate(
                img,
                new int[] {256, 256, 256},
                new FloatType(),
                AccessFlags.setOf(AccessFlags.VOLATILE),
                clijxFilter);

        // save the processed image
        N5Utils.save(
                filtered,
                new N5FSWriter("/Users/rossg/Documents/clij-data/n5/cell_culture_crop.n5"),
                "filtered/",
                new int[] {128, 128, 64},
                new GzipCompression()
        );
    }
}
