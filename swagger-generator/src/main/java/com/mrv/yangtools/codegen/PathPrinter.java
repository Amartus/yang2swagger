package com.mrv.yangtools.codegen;

import io.swagger.models.parameters.Parameter;

import java.util.Collection;
import java.util.function.Function;

/**
 * @author bartosz.michalik@amartus.com
 */
public abstract class PathPrinter {
    protected final PathSegment path;
    protected final Function<Collection<? extends Parameter>, String> paramPrinter;
    protected final Function<Collection<? extends Parameter>, String> lastParamPrinter;

    public PathPrinter(PathSegment path, Function<Collection<? extends Parameter>, String> paramPrinter) {
        this(path, paramPrinter, paramPrinter);
    }

    public PathPrinter(PathSegment path,
                       Function<Collection<? extends Parameter>, String> paramPrinter,
                       Function<Collection<? extends Parameter>, String> lastParamPrinter) {
        this.path = path;
        this.paramPrinter = paramPrinter;
        this.lastParamPrinter = lastParamPrinter;
    }


    public abstract String segment();
    public abstract String path();

}
