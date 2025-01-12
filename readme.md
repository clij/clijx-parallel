# CLIJx faCLon heavy
The faCLon heavy project will bring multi-GPU support for processing large images tile-by-tile. It is based on [imglib2](https://github.com/imglib), [n5](https://github.com/saalfeldlab/n5) and [CLIJ](https://clij.github.io).
It is the back-end for a user-interface to be built in the future. 
Thus, in order to use it right now, you need Java programming skills.

Work in progress.

# Usage
In order to use multiple GPUs, you need to define a [CLIJxPool](https://github.com/clij/clijx-faclon-heavy/blob/master/src/main/java/net/haesleinhuepf/clijx/faclonheavy/CLIJxPool.java) by configuring which GPU can hold how many CLIJx instances at a time.
For example, Intel integrated GPUs can typically hold a single OpenCL context only while dedicated AMD and NVidia cards
allow processing in multiple contexts at a time. If executed workflows and processed tiles are small enough so that 
multiple tiles can be processed in parallel on such dedicated GPUs, you can configure to do so:

```
CLIJxPool pool = CLIJxPool.fromDeviceNames(
            new String[]{"Intel(R) UHD Graphics 620", "RTX"},
            new int[]{                             1,     4});
System.out.println("CLIJ pool size: " + pool.size());
System.out.println("CLIJ pool: \n" + pool.log());
```
Output:
```
CLIJ pool size: 5
CLIJ pool: 
 * GeForce RTX 2080 Ti[net.haesleinhuepf.clijx.CLIJx@59d4cd39]
 * GeForce RTX 2080 Ti[net.haesleinhuepf.clijx.CLIJx@389c4eb1]
 * GeForce RTX 2080 Ti[net.haesleinhuepf.clijx.CLIJx@3fc79729]
 * GeForce RTX 2080 Ti[net.haesleinhuepf.clijx.CLIJx@34f6515b]
 * Intel(R) UHD Graphics 620[net.haesleinhuepf.clijx.CLIJx@4b34fff9]
```

Furthermore, you need to define your workflow as class implementing [TileProcessor](https://github.com/clij/clijx-faclon-heavy/blob/master/src/main/java/net/haesleinhuepf/clijx/faclonheavy/TileProcessor.java). 
To keep things simple, extend your workflow from [AbstractTileProcessor](https://github.com/clij/clijx-faclon-heavy/blob/master/src/main/java/net/haesleinhuepf/clijx/faclonheavy/AbstractTileProcessor.java).
An example is provided as [DummyFilter](https://github.com/clij/clijx-faclon-heavy/blob/master/src/main/java/net/haesleinhuepf/clijx/faclonheavy/implementations/DummyFilter.java), which basically is just a function:
```
@Override
public void accept(ClearCLBuffer input, ClearCLBuffer output) {
    // allocate temporary memory
    ClearCLBuffer temp = clijx.create(input);

    // process the image
    clijx.differenceOfGaussian(input, temp, 1, 1, 1, 5, 5, 5);
    clijx.thresholdOtsu(temp, output);

    // clean up
    temp.close();
}
```

Last but not least, you need to configure tile-size and margin for overlapping tiles:
```
int margin = 20;
int tile_size = 256;

final CLIJxFilterOp<FloatType, FloatType> clijxFilter =
        new CLIJxFilterOp<>(Views.extendMirrorSingle(floats), pool, DummyFilter.class, margin, margin, margin);

// make a result image lazily
final RandomAccessibleInterval<FloatType> filtered = Lazy.generate(
        img,
        new int[] {tile_size, tile_size, tile_size},
        new FloatType(),
        AccessFlags.setOf(AccessFlags.VOLATILE),
        clijxFilter);
```
Output:
```
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 949 ms
Finished processing on GeForce RTX 2080 Ti after 476 ms
Finished processing on GeForce RTX 2080 Ti after 1229 ms
Finished processing on GeForce RTX 2080 Ti after 973 ms
Start processing on Intel(R) UHD Graphics 620 image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 429 ms
Finished processing on GeForce RTX 2080 Ti after 434 ms
Finished processing on GeForce RTX 2080 Ti after 460 ms
Finished processing on GeForce RTX 2080 Ti after 351 ms
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 370 ms
Finished processing on GeForce RTX 2080 Ti after 376 ms
Finished processing on GeForce RTX 2080 Ti after 360 ms
Finished processing on GeForce RTX 2080 Ti after 395 ms
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 339 ms
Finished processing on GeForce RTX 2080 Ti after 313 ms
Finished processing on GeForce RTX 2080 Ti after 342 ms
Finished processing on GeForce RTX 2080 Ti after 375 ms
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 240 ms
Finished processing on GeForce RTX 2080 Ti after 206 ms
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 243 ms
Finished processing on Intel(R) UHD Graphics 620 after 15825 ms
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 151 ms
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on Intel(R) UHD Graphics 620 image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on GeForce RTX 2080 Ti after 282 ms
Finished processing on GeForce RTX 2080 Ti after 503 ms
Finished processing on GeForce RTX 2080 Ti after 414 ms
Start processing on GeForce RTX 2080 Ti image dimensions [296, 296, 296]
Finished processing on Intel(R) UHD Graphics 620 after 1162 ms
Finished processing on GeForce RTX 2080 Ti after 478 ms
...
```
Here you can see the first execution(s) take a bit longer because of the warmup-effect in our case dominated by OpenCl-code just in time compilation ([see](https://arxiv.org/ftp/arxiv/papers/2008/2008.11799.pdf)).
Furthermore, you can see that different GPUs need more/less time for computing the new tile.
Last but not least, the processed image is larger than the requested tile-size because of the define margin around every tile.
For optimal performance, keep the tiles as large as possible and minimize the margin.

A complete example is given in [this java file](https://github.com/clij/clijx-faclon-heavy/blob/master/src/test/java/net/haesleinhuepf/clijx/faclonheavy/Tutorial.java)

## Acknowledgements
This project was supported by the Deutsche Forschungsgemeinschaft under Germany’s Excellence Strategy – EXC2068 - Cluster of Excellence "Physics of Life" of TU Dresden.
This project has been made possible in part by grant number [2021-237734 (GPU-accelerating Fiji and friends using distributed CLIJ, NEUBIAS-style, EOSS4)](https://chanzuckerberg.com/eoss/proposals/gpu-accelerating-fiji-and-friends-using-distributed-clij-neubias-style/) from the Chan Zuckerberg Initiative DAF, an advised fund of the Silicon Valley Community Foundation.
