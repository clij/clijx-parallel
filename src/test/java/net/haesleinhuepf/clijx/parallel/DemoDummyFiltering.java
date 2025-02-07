package net.haesleinhuepf.clijx.parallel;

import bdv.cache.SharedQueue;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import ij.IJ;
import ij.ImagePlus;
import net.haesleinhuepf.clijx.parallel.implementations.DummyFilter;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.DOUBLE;
import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.type.PrimitiveType.INT;
import static net.imglib2.type.PrimitiveType.LONG;
import static net.imglib2.type.PrimitiveType.SHORT;

/**
 * Demos how to display an image live processed with a GPU Pool
 */
public class DemoDummyFiltering {

    public static void main(String[] args) throws MalformedURLException {

        File source_file = DatasetHelper.getDataset("https://zenodo.org/records/5101351/files/Raw_large.tif");

        double scale_factor = 1.5;

        ImagePlus imp = IJ.openImage(source_file.getAbsolutePath());
        // convert to float to prevent issues in LazyTutorial3
        IJ.run(imp, "32-bit", "");

        Img<FloatType> img = ImageJFunctions.convertFloat(imp);

        AffineTransform3D at = new AffineTransform3D();
        at.scale(scale_factor);

        final RealRandomAccessible<FloatType> field = Views.interpolate( Views.extendZero( img ) , new NLinearInterpolatorFactory<>());

        AffineRandomAccessible<FloatType, AffineGet> transformed = RealViews.affine(field, at);

        final IntervalView<FloatType> new_img = Views.interval(transformed, new long[]{0,0,0}, new long[]{(long) (imp.getWidth() * scale_factor), (long) (imp.getHeight() * scale_factor), (long) (imp.getNSlices() * scale_factor)});

        // If you want to specify how the pool is created:
        // CLIJPoolOptions.set("0:1, 1:1"); // Device 0: 1 thread, Device 1: 1 thread

        CLIJxPool pool = CLIJxPool.getInstance();
        System.out.println("GPU pool : "+pool);

        int margin = 20;
        int tile_size = 256;

        final CLIJxFilterOp<FloatType, FloatType> clijxFilter =
                new CLIJxFilterOp<>(Views.extendMirrorSingle(new_img), pool, DummyFilter.class, margin, margin, margin);

        // Make a result image lazily
        CachedCellImg<FloatType, ?> filtered = Lazy.generate(
                new_img,
                new int[]{tile_size, tile_size, tile_size},
                new FloatType(),
                AccessFlags.setOf(AccessFlags.VOLATILE),
                clijxFilter);


        boolean interactive = false;

        if (interactive) {
            BdvStackSource<Volatile<FloatType>> gpuProcessed =
                    BdvFunctions.show(
                            VolatileViews.wrapAsVolatile(
                                    filtered,
                                    new SharedQueue(pool.size()*2,1)), // You need to fetch with multiple cpu thread - otherwise GPU will be never be working in parallel
                            "Processed");

            BdvStackSource<FloatType> original =
                    BdvFunctions.show(
                            new_img,
                            "Original",
                            BdvOptions.options().addTo(gpuProcessed.getBdvHandle()));

            gpuProcessed.getConverterSetups().get(0).setDisplayRange(0,2);
            original.getConverterSetups().get(0).setDisplayRange(0,100);
            original.getConverterSetups().get(0).setColor(new ARGBType(ARGBType.rgba(250,0,0,255)));

            original.getBdvHandle().getSplitPanel().setCollapsed(false); // Opens split panel for bdv newbies - NPE because bdv vis tools is too old

        } else {

            Instant start = Instant.now();

            filtered.getCells()
                    .parallelStream()// Parallel magic, comment to go serial
                    .forEach(Cell::getData);

            // Nico: on my laptop with a A500 and an Iris Xe card:
            //    - parallel: 34s
            //    - serial: 72s

            Duration processingDuration = Duration.between(start, Instant.now());
            System.out.println("Filtering duration = "+processingDuration.toMillis()+" ms");
            ImageJFunctions.show(filtered,"filtered");
        }

    }

    /**
     * A utility class that helps loading and caching file from the internet
     */
    public static class DatasetHelper {

        public static final File cachedSampleDir = new File(System.getProperty("user.home"),"CachedSamples");

        public static File urlToFile(URL url, Function<String, String> decoder) {
            try {
                File file_out = new File(cachedSampleDir,decoder.apply(url.getFile()));
                if (file_out.exists()) {
                    return file_out;
                } else {
                    System.out.println("Downloading and caching: "+url+" size = "+ (getFileSize(url)/1024) +" kb");
                    FileUtils.copyURLToFile(url, file_out, 10000, 10000);
                    System.out.println("Downloading and caching of "+url+" completed successfully ");
                    return file_out;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        static final Function<String, String> decoder = (str) -> {
            try {
                return URLDecoder.decode(str, "UTF-8");
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        };

        public static File getDataset(String urlString) throws MalformedURLException {
            return getDataset(urlString, decoder);
        }

        public static File getDataset(String urlString, Function<String, String> decoder) throws MalformedURLException {
            return urlToFile(new URL(urlString), decoder);
        }

        // https://stackoverflow.com/questions/12800588/how-to-calculate-a-file-size-from-url-in-java
        private static int getFileSize(URL url) {
            URLConnection conn = null;
            try {
                conn = url.openConnection();
                if(conn instanceof HttpURLConnection) {
                    ((HttpURLConnection)conn).setRequestMethod("HEAD");
                }
                conn.getInputStream();
                return conn.getContentLength();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if(conn instanceof HttpURLConnection) {
                    ((HttpURLConnection)conn).disconnect();
                }
            }
        }

    }
    
    /**
     * Convenience methods to create lazy evaluated cached cell images with ops or consumers.
     *
     * @author Stephan Saalfeld
     */
    public interface Lazy {

        /**
         * Create a memory {@link CachedCellImg} with a cell {@link Cache}.  Unless
         * you are doing something special, you will likely not use this method.
         *
         * @param grid
         * @param cache
         * @param type
         * @param accessFlags
         * @return
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
         static <T extends NativeType<T>> CachedCellImg<T, ?> createImg(
                final CellGrid grid,
                final Cache<Long, Cell<?>> cache,
                final T type,
                final Set<AccessFlags> accessFlags) {

            final CachedCellImg<T, ?> img;

            if (type instanceof GenericByteType) {
                img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(BYTE, accessFlags));
            } else if (type instanceof GenericShortType) {
                img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(SHORT, accessFlags));
            } else if (type instanceof GenericIntType) {
                img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(INT, accessFlags));
            } else if (type instanceof GenericLongType) {
                img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(LONG, accessFlags));
            } else if (type instanceof FloatType) {
                img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(FLOAT, accessFlags));
            } else if (type instanceof DoubleType) {
                img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(DOUBLE, accessFlags));
            } else {
                img = null;
            }
            return img;
        }

        /**
         * Create a memory {@link CachedCellImg} with a {@link CellLoader}.
         * Unless you are doing something special, you will likely not use this
         * method.
         *
         * @param targetInterval
         * @param blockSize
         * @param type
         * @param accessFlags
         * @param loader
         * @return
         */
         static <T extends NativeType<T>> CachedCellImg<T, ?> createImg(
                final Interval targetInterval,
                final int[] blockSize,
                final T type,
                final Set<AccessFlags> accessFlags,
                final CellLoader<T> loader) {

            final long[] dimensions = Intervals.dimensionsAsLongArray(targetInterval);
            final CellGrid grid = new CellGrid(dimensions, blockSize);

            @SuppressWarnings({"unchecked", "rawtypes"})
            final Cache<Long, Cell<?>> cache =
                    new SoftRefLoaderCache().withLoader(LoadedCellCacheLoader.get(grid, loader, type, accessFlags));

            return createImg(grid, cache, type, accessFlags);
        }

        /**
         * Create a memory {@link CachedCellImg} with a cell generator implemented
         * as a {@link Consumer}.  This is the most general purpose method for
         * anything new.  Note that any inputs are managed by the cell generator,
         * not but this method.
         *
         * @param targetInterval
         * @param blockSize
         * @param type
         * @param accessFlags
         * @param op
         * @return
         */
         static <T extends NativeType<T>> CachedCellImg<T, ?> generate(
                final Interval targetInterval,
                final int[] blockSize,
                final T type,
                final Set<AccessFlags> accessFlags,
                final Consumer<RandomAccessibleInterval<T>> op) {

            return createImg(
                    targetInterval,
                    blockSize,
                    type,
                    accessFlags,
                    op::accept);
        }

    }

}
