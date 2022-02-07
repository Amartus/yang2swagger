/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen;

import com.google.common.base.Strings;
import io.swagger.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Allows for conversion of {@link PathSegment} to strings describing resources
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public abstract class PathPrinter {
    protected final boolean useModuleName;
    protected final PathSegment path;
    protected final Function<Collection<? extends Parameter>, String> paramPrinter;
    protected final Function<Collection<? extends Parameter>, String> lastParamPrinter;

    /**
     * Path printer that uses the same param conversion function for all segments
     * @param path segment
     * @param paramPrinter convert parameter to string
     * @param useModuleName
     */
    public PathPrinter(PathSegment path, Function<Collection<? extends Parameter>, String> paramPrinter, boolean useModuleName) {
        this(useModuleName, path, paramPrinter, paramPrinter);
    }

    /**
     * Path printer that uses the same param conversion function for all segments
     * @param useModuleName
     * @param path segment
     * @param paramPrinter convert parameter to string for parent segments of 'path'
     * @param lastParamPrinter convert parameter to string for 'path'
     */
    public PathPrinter(boolean useModuleName, PathSegment path,
                       Function<Collection<? extends Parameter>, String> paramPrinter,
                       Function<Collection<? extends Parameter>, String> lastParamPrinter) {
        this.useModuleName = useModuleName;
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
     * Convert segment to full path taking into account parent segments
     * @return resource string
     */
    public String path() {
        LinkedList<PathSegment> result = new LinkedList<>();

        PathSegment parent = path.drop();

        String lastSegment = segment(lastParamPrinter, path.getModuleName(), path);



        for(PathSegment p : parent) {
            result.addFirst(p);
        }

        String path = result.stream().map(s -> segment(paramPrinter, s.getModuleName(), s))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("/"));
        if(StringUtils.isNotBlank(path)) {
            return path + "/" + lastSegment;
        }
        return lastSegment;
    }

    private String removeTrailingSlash(String segment) {
        if(!path.isCollection()  && segment.endsWith("/")) {
            return segment.substring(0, segment.length() - 1);
        }

        return segment;
    }

    protected Optional<String> parentModuleName(PathSegment segment) {
        return Optional
                .ofNullable(segment.parent())
                .filter(parent -> parent.getName() != null)
                .flatMap(parent -> Optional.ofNullable(parent.getModuleName()));
    }

    protected String segment(Function<Collection<? extends Parameter>, String> paramWriter, String moduleName, PathSegment seg) {
        if(seg.getName() == null) return "";
        boolean shouldUseModuleName = useModuleName && !Strings.isNullOrEmpty(moduleName);
        Supplier<Boolean> differentFromParent = () -> ! parentModuleName(seg)
                .filter(moduleName::equals)
                .isPresent();

        final String prefix = shouldUseModuleName && differentFromParent.get() ? moduleName + ":" : "";
        return prefix + seg.getName() + paramWriter.apply(seg.getParam());
    }

}
