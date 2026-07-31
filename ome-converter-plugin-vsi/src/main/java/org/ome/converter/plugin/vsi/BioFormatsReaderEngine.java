package org.ome.converter.plugin.vsi;

import loci.common.DebugTools;
import loci.formats.ClassList;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.meta.IMetadata;

import org.ome.converter.core.exception.MetadataException;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.VendorMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class BioFormatsReaderEngine implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BioFormatsReaderEngine.class);

    private final File sourceFile;
    private IFormatReader reader;
    private IMetadata metadataStore;
    private boolean initialized = false;
    private List<Integer> cachedPyramidSeries;

    static {
        try {
            DebugTools.setRootLevel("WARN");
        } catch (Throwable ignored) {}
    }

    public BioFormatsReaderEngine(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public synchronized void initialize() throws MetadataException {
        if (initialized) return;

        try {
            ClassList<IFormatReader> classList = new ClassList<>(IFormatReader.class);
            try {
                @SuppressWarnings("unchecked")
                Class<? extends IFormatReader> cellSensClass = (Class<? extends IFormatReader>) Class.forName("loci.formats.in.CellSensReader");
                classList.addClass(cellSensClass);
            } catch (ClassNotFoundException e) {
                log.debug("loci.formats.in.CellSensReader not explicitly found", e);
            }

            reader = (classList.getClasses() != null && classList.getClasses().length > 0)
                ? new ImageReader(classList)
                : new ImageReader();
            reader.setOriginalMetadataPopulated(true);

            try {
                loci.formats.services.OMEXMLService service =
                    new loci.common.services.ServiceFactory().getInstance(loci.formats.services.OMEXMLService.class);
                metadataStore = service.createOMEXMLMetadata();
                reader.setMetadataStore(metadataStore);
            } catch (Exception e) {
                log.warn("OMEXMLService not available, using default metadata parsing", e);
            }

            log.info("Opening VSI file with Bio-Formats ImageReader: {}", sourceFile.getAbsolutePath());
            reader.setId(sourceFile.getAbsolutePath());
            initialized = true;
            log.info("Successfully initialized Bio-Formats reader for VSI file: {}", sourceFile.getName());
        } catch (Exception e) {
            log.error("Failed to initialize Bio-Formats reader for file: {}", sourceFile.getAbsolutePath(), e);
            throw new MetadataException("Failed to open VSI file via Bio-Formats (ensure companion folder exists and is complete): " + sourceFile.getName() + " -> " + e.getMessage(), e);
        }
    }

    public List<Integer> getPyramidSeriesIndices() throws MetadataException {
        initialize();
        if (cachedPyramidSeries != null) return cachedPyramidSeries;

        List<Integer> list = new ArrayList<>();
        list.add(0); // Main image series

        reader.setSeries(0);
        int lastW = reader.getSizeX();
        int lastH = reader.getSizeY();

        int count = reader.getSeriesCount();
        for (int s = 1; s < count; s++) {
            reader.setSeries(s);
            int w = reader.getSizeX();
            int h = reader.getSizeY();
            if (w < lastW && h < lastH && (double) w / lastW <= 0.75 && (double) h / lastH <= 0.75) {
                list.add(s);
                lastW = w;
                lastH = h;
            }
        }
        reader.setSeries(0);
        this.cachedPyramidSeries = Collections.unmodifiableList(list);
        log.info("Detected {} true multi-resolution pyramid levels out of {} total VSI series", list.size(), count);
        return cachedPyramidSeries;
    }

    public ImageMetadata extractStandardMetadata() throws MetadataException {
        initialize();
        try {
            List<Integer> pyramidIndices = getPyramidSeriesIndices();
            int pyramidLevels = pyramidIndices.size();

            reader.setSeries(0);
            int sizeX = reader.getSizeX();
            int sizeY = reader.getSizeY();
            int sizeZ = reader.getSizeZ();
            int sizeC = reader.getSizeC();
            int sizeT = reader.getSizeT();

            double physX = 0.325;
            double physY = 0.325;
            double physZ = 1.000;

            if (metadataStore != null && metadataStore.getImageCount() > 0) {
                try {
                    if (metadataStore.getPixelsPhysicalSizeX(0) != null) {
                        physX = metadataStore.getPixelsPhysicalSizeX(0).value().doubleValue();
                    }
                    if (metadataStore.getPixelsPhysicalSizeY(0) != null) {
                        physY = metadataStore.getPixelsPhysicalSizeY(0).value().doubleValue();
                    }
                    if (metadataStore.getPixelsPhysicalSizeZ(0) != null) {
                        physZ = metadataStore.getPixelsPhysicalSizeZ(0).value().doubleValue();
                    }
                } catch (Exception e) {
                    log.debug("Using fallback physical dimensions for VSI image", e);
                }
            }

            String pixelType = loci.formats.FormatTools.getPixelTypeString(reader.getPixelType());
            int bytesPerPixel = loci.formats.FormatTools.getBytesPerPixel(reader.getPixelType());

            List<ImageMetadata.ChannelInfo> channels = new ArrayList<>();
            for (int c = 0; c < sizeC; c++) {
                String name = "Channel " + (c + 1);
                String color = switch (c % 4) {
                    case 0 -> "00FF00";
                    case 1 -> "FF0000";
                    case 2 -> "0000FF";
                    default -> "FFFF00";
                };
                if (metadataStore != null && c < metadataStore.getChannelCount(0)) {
                    try {
                        if (metadataStore.getChannelName(0, c) != null) {
                            name = metadataStore.getChannelName(0, c);
                        }
                    } catch (Exception ignored) {}
                }
                channels.add(new ImageMetadata.ChannelInfo(c, name, color, 0, (1L << (bytesPerPixel * 8)) - 1));
            }

            return new ImageMetadata(
                sourceFile.getName(),
                sizeX, sizeY, sizeZ, sizeC, sizeT,
                physX, physY, physZ,
                "micrometer", "micrometer", "micrometer",
                pixelType, bytesPerPixel, pyramidLevels,
                channels
            );
        } catch (Exception e) {
            throw new MetadataException("Failed to extract standard OME metadata from VSI", e);
        }
    }

    public VendorMetadata extractVendorMetadata() throws MetadataException {
        initialize();
        try {
            Map<String, String> globalTags = new LinkedHashMap<>();
            Hashtable<String, Object> bfGlobal = reader.getGlobalMetadata();
            if (bfGlobal != null) {
                for (Map.Entry<String, Object> entry : bfGlobal.entrySet()) {
                    globalTags.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            Map<String, Map<String, String>> seriesTags = new LinkedHashMap<>();
            List<Integer> pyramidIndices = getPyramidSeriesIndices();
            for (int s : pyramidIndices) {
                reader.setSeries(s);
                Map<String, String> sMap = new LinkedHashMap<>();
                Hashtable<String, Object> bfSeries = reader.getSeriesMetadata();
                if (bfSeries != null) {
                    for (Map.Entry<String, Object> entry : bfSeries.entrySet()) {
                        sMap.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
                seriesTags.put("Series_" + s, sMap);
            }

            reader.setSeries(0);

            String xmlDump = "";
            if (metadataStore != null) {
                try {
                    loci.formats.services.OMEXMLService service =
                        new loci.common.services.ServiceFactory().getInstance(loci.formats.services.OMEXMLService.class);
                    xmlDump = service.getOMEXML(metadataStore);
                } catch (Exception ignored) {}
            }

            return new VendorMetadata("Olympus CellSens VSI", globalTags, seriesTags, xmlDump);
        } catch (Exception e) {
            throw new MetadataException("Failed to extract raw vendor metadata from VSI file", e);
        }
    }

    public int getIndex(int seriesIndex, int z, int c, int t) throws MetadataException {
        initialize();
        reader.setSeries(seriesIndex);
        int maxIndex = Math.max(1, reader.getImageCount()) - 1;
        try {
            int idx = reader.getIndex(z, c, t);
            return Math.min(Math.max(0, idx), maxIndex);
        } catch (Exception e) {
            return 0;
        }
    }

    public byte[] readPlane(int seriesIndex, int noIndex) throws Exception {
        initialize();
        reader.setSeries(seriesIndex);
        int validIndex = Math.min(Math.max(0, noIndex), Math.max(1, reader.getImageCount()) - 1);
        return reader.openBytes(validIndex);
    }

    public byte[] readTile(int seriesIndex, int noIndex, int x, int y, int w, int h) throws Exception {
        initialize();
        reader.setSeries(seriesIndex);
        int validIndex = Math.min(Math.max(0, noIndex), Math.max(1, reader.getImageCount()) - 1);
        return reader.openBytes(validIndex, x, y, w, h);
    }

    public int getSizeX(int seriesIndex) throws MetadataException {
        initialize();
        reader.setSeries(seriesIndex);
        return reader.getSizeX();
    }

    public int getSizeY(int seriesIndex) throws MetadataException {
        initialize();
        reader.setSeries(seriesIndex);
        return reader.getSizeY();
    }

    public int getSizeZ(int seriesIndex) throws MetadataException {
        initialize();
        reader.setSeries(seriesIndex);
        return reader.getSizeZ();
    }

    public int getSizeC(int seriesIndex) throws MetadataException {
        initialize();
        reader.setSeries(seriesIndex);
        return reader.getSizeC();
    }

    public int getSizeT(int seriesIndex) throws MetadataException {
        initialize();
        reader.setSeries(seriesIndex);
        return reader.getSizeT();
    }

    @Override
    public synchronized void close() {
        if (reader != null) {
            try {
                reader.close();
                log.info("Closed Bio-Formats reader for {}", sourceFile.getName());
            } catch (Exception e) {
                log.warn("Error closing Bio-Formats reader", e);
            }
            reader = null;
            initialized = false;
        }
    }
}
