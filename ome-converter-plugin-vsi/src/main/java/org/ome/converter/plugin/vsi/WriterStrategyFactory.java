package org.ome.converter.plugin.vsi;

import org.ome.converter.core.model.OmeZarrVersion;

public class WriterStrategyFactory {
    public static OmeZarrWriterStrategy getWriter(OmeZarrVersion version) {
        if (version == OmeZarrVersion.OME_ZARR_0_4) {
            return new OmeZarrV04Writer();
        }
        return new OmeZarrV05Writer();
    }
}
