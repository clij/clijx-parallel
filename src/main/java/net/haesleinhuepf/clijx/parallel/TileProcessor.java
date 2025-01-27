package net.haesleinhuepf.clijx.parallel;

import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clijx.CLIJx;

import java.util.function.BiConsumer;

/**
 * Note: Those workflows should not result in label images as identical labels can exist in neighboring tiles.
 * Workflows should result in filtered intensity images, binary images or parametric maps only.
 *
 * @author: Robert Haase
 */
public interface TileProcessor extends BiConsumer<ClearCLBuffer, ClearCLBuffer> {
    void setCLIJx(CLIJx clijx);
}
