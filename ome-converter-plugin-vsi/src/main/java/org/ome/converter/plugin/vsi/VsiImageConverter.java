package org.ome.converter.plugin.vsi;

import org.ome.converter.core.api.ImageConverter;
import org.ome.converter.core.api.ProgressObserver;
import org.ome.converter.core.exception.ConversionException;
import org.ome.converter.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class VsiImageConverter implements ImageConverter {
    private static final Logger log = LoggerFactory.getLogger(VsiImageConverter.class);
    private static final long MAX_IN_MEMORY_PLANE_BYTES = 250 * 1024 * 1024L; // 250 MB max per plane buffer

    @Override
    public ConversionResult convert(ConversionRequest request, ProgressObserver observer) throws ConversionException {
        Instant startTime = Instant.now();
        File sourceFile = request.sourceFile().toFile();
        Path targetDir = request.targetDestinationDirectory();

        log.info("Starting VSI -> OME-Zarr 0.5 (Zarr v3) conversion for: {}", sourceFile.getAbsolutePath());
        if (observer != null) {
            observer.onLog("INFO", "Starting conversion of VSI image: " + sourceFile.getName());
            observer.onProgress(1.0, 0, 100, "[1.0%] Initializing Bio-Formats Reader & Reading VSI File...");
        }

        try (BioFormatsReaderEngine reader = new BioFormatsReaderEngine(sourceFile)) {
            // 1. Extract standard & vendor metadata
            if (observer != null) {
                observer.onProgress(2.0, 0, 100, "[2.0%] Parsing VSI Image Dimensions & Spatial Metadata...");
            }
            ImageMetadata standardMetadata = reader.extractStandardMetadata();

            if (observer != null) {
                observer.onProgress(3.0, 0, 100, "[3.0%] Harvesting Proprietary VSI Hardware Tags & XML Headers...");
            }
            VendorMetadata vendorMetadata = request.preserveVendorMetadata()
                ? reader.extractVendorMetadata()
                : VendorMetadata.empty("Olympus CellSens VSI");

            List<Integer> pyramidSeries = reader.getPyramidSeriesIndices();
            int levels = pyramidSeries.size();
            int bytesPerPixel = Math.max(1, standardMetadata.bytesPerPixel());

            log.info("Extracted metadata for VSI image. Dimensions: {}x{}, Channels: {}, Pyramid Levels: {}",
                standardMetadata.sizeX(), standardMetadata.sizeY(), standardMetadata.sizeC(), levels);

            if (observer != null) {
                observer.onLog("INFO", String.format("Image Dimensions: %dx%d, Channels: %d, Pyramid Levels: %d",
                    standardMetadata.sizeX(), standardMetadata.sizeY(), standardMetadata.sizeC(), levels));
                observer.onLog("INFO", "Harvested " + vendorMetadata.globalTags().size() + " raw vendor metadata tags");
                observer.onProgress(4.0, 0, 100, String.format("[4.0%%] Creating %s Dataset Folder Structure...", request.targetVersion().getDisplayName()));
            }

            // 2. Select Writer Strategy & Initialize Dataset
            OmeNgffMetadataBuilder omeBuilder = new OmeNgffMetadataBuilder();
            Map<String, Object> omeJson = omeBuilder.buildOmeMetadata(standardMetadata);

            OmeZarrWriterStrategy writer = WriterStrategyFactory.getWriter(request.targetVersion());
            Path zarrRoot = writer.initializeDatasetDirectory(targetDir, sourceFile.getName());

            // 3. Write Root Metadata & bioformats2raw Companion OME-XML Node
            writer.writeRootMetadata(zarrRoot, omeJson, vendorMetadata);
            if (vendorMetadata != null && vendorMetadata.rawXmlHeader() != null) {
                writer.writeCompanionXml(zarrRoot, vendorMetadata.rawXmlHeader());
            }

            ChunkSpec chunkSpec = request.chunkSpec();

            // Calculate total tiles across all pyramid levels
            long totalTiles = 0;
            for (int l = 0; l < levels; l++) {
                int sIndex = pyramidSeries.get(l);
                int lvlW = reader.getSizeX(sIndex);
                int lvlH = reader.getSizeY(sIndex);
                int sC = reader.getSizeC(sIndex);
                int sZ = reader.getSizeZ(sIndex);
                int sT = reader.getSizeT(sIndex);
                int tilesX = (int) Math.ceil((double) lvlW / chunkSpec.tileWidth());
                int tilesY = (int) Math.ceil((double) lvlH / chunkSpec.tileHeight());
                totalTiles += (long) tilesX * tilesY * sC * sT * sZ;
            }

            long processedTiles = 0;
            long bytesWritten = 0;

            for (int level = 0; level < levels; level++) {
                if (observer != null && observer.isCancelled()) {
                    log.warn("Conversion job {} was cancelled by user", request.jobId());
                    return new ConversionResult(request.jobId(), zarrRoot, ConversionResult.Status.CANCELLED, processedTiles, bytesWritten, Duration.between(startTime, Instant.now()), "Job cancelled by user", null);
                }

                int seriesIndex = pyramidSeries.get(level);
                int levelW = reader.getSizeX(seriesIndex);
                int levelH = reader.getSizeY(seriesIndex);

                Path levelPath = zarrRoot.resolve(String.valueOf(level));
                writer.writeArrayMetadata(levelPath, standardMetadata, level, chunkSpec, levelW, levelH);

                int tileW = chunkSpec.tileWidth();
                int tileH = chunkSpec.tileHeight();
                int tilesX = (int) Math.ceil((double) levelW / tileW);
                int tilesY = (int) Math.ceil((double) levelH / tileH);

                long planeBytesNeeded = (long) levelW * levelH * bytesPerPixel;
                boolean useFastPlaneBuffering = planeBytesNeeded <= MAX_IN_MEMORY_PLANE_BYTES;

                int sT = reader.getSizeT(seriesIndex);
                int sC = reader.getSizeC(seriesIndex);
                int sZ = reader.getSizeZ(seriesIndex);

                for (int t = 0; t < sT; t++) {
                    for (int c = 0; c < sC; c++) {
                        for (int z = 0; z < sZ; z++) {
                            int planeIndex = reader.getIndex(seriesIndex, z, c, t);

                            byte[] fullPlaneBytes = null;
                            if (useFastPlaneBuffering) {
                                try {
                                    fullPlaneBytes = reader.readPlane(seriesIndex, planeIndex);
                                } catch (Exception e) {
                                    log.warn("Failed fast plane buffer read for series {}, plane {}, falling back to tile reads", seriesIndex, planeIndex, e);
                                    useFastPlaneBuffering = false;
                                }
                            }

                            for (int ty = 0; ty < tilesY; ty++) {
                                for (int tx = 0; tx < tilesX; tx++) {
                                    if (observer != null && observer.isCancelled()) {
                                        return new ConversionResult(request.jobId(), zarrRoot, ConversionResult.Status.CANCELLED, processedTiles, bytesWritten, Duration.between(startTime, Instant.now()), "Job cancelled", null);
                                    }

                                    int pixelX = tx * tileW;
                                    int pixelY = ty * tileH;
                                    int currentW = Math.min(tileW, levelW - pixelX);
                                    int currentH = Math.min(tileH, levelH - pixelY);

                                    byte[] rawTileBytes;
                                    if (useFastPlaneBuffering && fullPlaneBytes != null) {
                                        rawTileBytes = cropTileFromPlane(fullPlaneBytes, levelW, pixelX, pixelY, currentW, currentH, bytesPerPixel);
                                    } else {
                                        rawTileBytes = reader.readTile(seriesIndex, planeIndex, pixelX, pixelY, currentW, currentH);
                                    }

                                    long written = writer.writeChunkData(levelPath, t, c, z, ty, tx, rawTileBytes, chunkSpec);
                                    bytesWritten += written;
                                    processedTiles++;

                                    if (observer != null) {
                                        double percent = (double) processedTiles / totalTiles * 100.0;
                                        String taskMsg = String.format("[%.1f%%] Converting Level %d/%d (Tile %d/%d)",
                                            percent, level + 1, levels, processedTiles, totalTiles);
                                        observer.onProgress(percent, processedTiles, totalTiles, taskMsg);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            log.info("Conversion completed successfully in {} ms. Dataset saved at {}", elapsed.toMillis(), zarrRoot.toAbsolutePath());
            if (observer != null) {
                observer.onProgress(100.0, processedTiles, totalTiles, "[100.0%] Conversion Complete!");
                observer.onLog("INFO", "Conversion completed successfully in " + elapsed.toSeconds() + " seconds. Saved to: " + zarrRoot.toAbsolutePath());
            }

            return ConversionResult.successWithMetadata(request.jobId(), zarrRoot, processedTiles, bytesWritten, elapsed, standardMetadata, vendorMetadata);

        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.error("Error during VSI conversion for job {}", request.jobId(), e);
            if (observer != null) {
                observer.onProgress(0.0, 0, 0, "❌ Error: " + e.getMessage());
                observer.onLog("ERROR", "Conversion failed: " + e.getMessage());
            }
            throw new ConversionException("Failed to convert VSI file to OME-Zarr 0.5: " + e.getMessage(), e);
        }
    }

    private byte[] cropTileFromPlane(byte[] plane, int fullW, int tileX, int tileY, int tileW, int tileH, int bpp) {
        byte[] tile = new byte[tileW * tileH * bpp];
        for (int r = 0; r < tileH; r++) {
            int srcPos = ((tileY + r) * fullW + tileX) * bpp;
            int destPos = (r * tileW) * bpp;
            if (srcPos + tileW * bpp <= plane.length) {
                System.arraycopy(plane, srcPos, tile, destPos, tileW * bpp);
            }
        }
        return tile;
    }
}
