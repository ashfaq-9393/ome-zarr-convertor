package org.ome.converter.core.api;

import org.ome.converter.core.exception.MetadataException;
import org.ome.converter.core.model.ImageMetadata;

import java.io.File;

public interface MetadataConverter {
    ImageMetadata extractStandardMetadata(File sourceFile) throws MetadataException;
}
