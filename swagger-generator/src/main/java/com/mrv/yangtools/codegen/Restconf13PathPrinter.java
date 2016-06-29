package com.mrv.yangtools.codegen;

import io.swagger.models.parameters.Parameter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Restconf13PathPrinter extends PathPrinter {

    private static final Function<Collection<? extends Parameter>, String> param =
            params -> params.isEmpty() ? "/" :
                    "=" + params.stream().map(p -> "{" + p.getName() + "}").collect(Collectors.joining(",")) + "/";

    private final boolean useModuleName;

    public Restconf13PathPrinter(PathSegment path, boolean useModuleName) {
        this(path, useModuleName, false);
    }

    public Restconf13PathPrinter(PathSegment path, boolean useModuleName, boolean dropLastParams) {
        super(path, param, dropLastParams ? x -> "/" : param);
        this.useModuleName = useModuleName;
    }

    @Override
    public String segment() {
        return segment(paramPrinter, path.getModuleName(), path);

    }

    protected String segment(Function<Collection<? extends Parameter>, String> paramWriter, String moduleName, PathSegment seg) {
        return (useModuleName && moduleName != null && !moduleName.isEmpty() ? moduleName + ":" : "") + seg.getName() + paramWriter.apply(seg.getParam());
    }

    /**
     *
     * @return for full path
     */
    @Override
    public String path() {
        LinkedList<PathSegment> result = new LinkedList<>();

        PathSegment parent = path.drop();

        String lastSegment = segment(lastParamPrinter, path.getModuleName(), path);

        for(PathSegment p : parent) {
            result.addFirst(p);
        }

        return result.stream().map(s -> segment(paramPrinter, s.getModuleName(), s)).collect(Collectors.joining()) + lastSegment;

    }

}
