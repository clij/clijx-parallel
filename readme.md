# CLIJx Parallel
The CLIJx parallel project brings multi-GPU support into CLIJ. 
It is used to define a pool of CLIJx instances spanning one or several GPUs (that are part of the same JVM - i.e. it's not a tool to send jobs to a computing cluster).

It allows notably to process large images tile-by-tile.It is based on [imglib2](https://github.com/imglib) and [CLIJ](https://clij.github.io).
It is the back-end for a user-interface to be built in the future. 
Thus, in order to use it right now, you need Java programming skills.

Work in progress.

# Usage
In order to parallelize your processing on GPUs, you need to define a [CLIJxPool](https://github.com/clij/clijx-parallel/blob/master/src/main/java/net/haesleinhuepf/clijx/parallel/CLIJxPool.java).
The easiest is to call `CLIJxPool.getInstance()` which will create a pool of 
`CLIJx` instances that will use all CLIJ compatible devices available and will even split big GPU cards into several `CLIJx` instances.


Indeed, while Intel integrated GPUs typically hold a single OpenCL context, dedicated AMD and NVidia cards
allow processing in multiple contexts at a time. 

The default GPU pool created can be modified (see Javadoc), if some GPUs are unable to process particular jobs due to 
different hardware or memory issues if the device is split in too many contexts.

The pool can be used directly in a multithreaded workflow where each CLIJx instance has to be acquired from the pool by 
calling `pool.getIdleCLIJx()` and returned back to the pool by calling `pool.setCLIJxIdle(clijx)`.

If the workflow consists of processing an image tile by tile, you can directly use the classes present in this repository:
you need to define your workflow as class implementing [TileProcessor](https://github.com/clij/clijx-parallel/blob/master/src/main/java/net/haesleinhuepf/clijx/parallel/TileProcessor.java). 
To keep things simple, extend your workflow from [AbstractTileProcessor](https://github.com/clij/clijx-parallel/blob/master/src/main/java/net/haesleinhuepf/clijx/parallel/AbstractTileProcessor.java).
An example is provided as [DummyFilter](https://github.com/clij/clijx-parallel/blob/master/src/main/java/net/haesleinhuepf/clijx/parallel/implementations/DummyFilter.java), which basically is just a function:
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

A complete example is given in [this java file](https://github.com/clij/clijx-parallel/blob/master/src/test/java/net/haesleinhuepf/clijx/parallel/Tutorial.java)

## Acknowledgements
This project was supported by the Deutsche Forschungsgemeinschaft under Germany’s Excellence Strategy – EXC2068 - Cluster of Excellence "Physics of Life" of TU Dresden.
This project has been made possible in part by grant number [2021-237734 (GPU-accelerating Fiji and friends using distributed CLIJ, NEUBIAS-style, EOSS4)](https://chanzuckerberg.com/eoss/proposals/gpu-accelerating-fiji-and-friends-using-distributed-clij-neubias-style/) from the Chan Zuckerberg Initiative DAF, an advised fund of the Silicon Valley Community Foundation.
