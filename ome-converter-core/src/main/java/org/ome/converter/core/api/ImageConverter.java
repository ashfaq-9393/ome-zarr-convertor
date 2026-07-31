package org.ome.converter.core.api;

import org.ome.converter.core.exception.ConversionException;
import org.ome.converter.core.model.ConversionRequest;
import org.ome.converter.core.model.ConversionResult;

public interface ImageConverter {
    ConversionResult convert(ConversionRequest request, ProgressObserver observer) throws ConversionException;
}
