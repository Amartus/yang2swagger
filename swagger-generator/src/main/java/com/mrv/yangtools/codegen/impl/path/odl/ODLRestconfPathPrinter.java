/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */

package com.mrv.yangtools.codegen.impl.path.odl;

import com.mrv.yangtools.codegen.PathPrinter;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.parameters.Parameter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link PathPrinter} compliant with https://tools.ietf.org/html/draft-bierman-netconf-restconf-02#section-5.3.1
 * use by Open https://wiki.opendaylight.org/view/OpenDaylight_Controller:MD-SAL:Restconf#Identifier_in_URI
 */
public class ODLRestconfPathPrinter extends PathPrinter {

    private static final Function<Collection<? extends Parameter>, String> param =
        params -> params.isEmpty() ? "/" : "/" + params.stream().map(p -> "{" + p.getName() + "}").collect(Collectors.joining("/")) + "/";

    private final boolean useModuleName;

    public ODLRestconfPathPrinter(PathSegment path, boolean useModuleName) {
        this(path, useModuleName, false);
    }

    public ODLRestconfPathPrinter(PathSegment path, boolean useModuleName, boolean dropLastParams) {
        super(path, param, dropLastParams ? x -> "/" : param);
        this.useModuleName = useModuleName;
    }

    @Override
    public String segment() {
        return segment(paramPrinter, path.getModuleName(), path);

    }

    protected String segment(Function<Collection<? extends Parameter>, String> paramWriter, String moduleName, PathSegment seg) {
        if(seg.getName() == null) return "";
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
