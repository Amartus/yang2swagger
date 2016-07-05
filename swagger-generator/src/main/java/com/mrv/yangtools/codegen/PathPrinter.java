package com.mrv.yangtools.codegen;

import io.swagger.models.parameters.Parameter;

import java.util.Collection;
import java.util.function.Function;

/**
 * Allows for conversion of {@link PathSegment} to strings describing resources
 * @author bartosz.michalik@amartus.com
 */
public abstract class PathPrinter {
    protected final PathSegment path;
    protected final Function<Collection<? extends Parameter>, String> paramPrinter;
    protected final Function<Collection<? extends Parameter>, String> lastParamPrinter;

    /**
     * Path printer that uses the same param conversion function for all segments
     * @param path segment
     * @param paramPrinter convert parameter to string
     */
    public PathPrinter(PathSegment path, Function<Collection<? extends Parameter>, String> paramPrinter) {
        this(path, paramPrinter, paramPrinter);
    }

    /**
     * Path printer that uses the same param conversion function for all segments
     * @param path segment
     * @param paramPrinter convert parameter to string for partent segments of 'path'
     * @param lastParamPrinter convert parameter to string for 'path'
     */
    public PathPrinter(PathSegment path,
                       Function<Collection<? extends Parameter>, String> paramPrinter,
                       Function<Collection<? extends Parameter>, String> lastParamPrinter) {
        this.path = path;
        this.paramPrinter = paramPrinter;
        this.lastParamPrinter = lastParamPrinter;
    }


    /**
     * Convert segment - do not use parent segments
     * @return resource string
     */
    public abstract String segment();
    /**
     * Convert segment to full path taking into account partent segments
     * @return resource string
     */
    public abstract String path();

}
