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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link PathPrinter} compliant with https://tools.ietf.org/html/draft-bierman-netconf-restconf-02#section-5.3.1
 * use by Open https://wiki.opendaylight.org/view/OpenDaylight_Controller:MD-SAL:Restconf#Identifier_in_URI
 */
public class ODLRestconfPathPrinter extends PathPrinter {

    private static final Function<Collection<? extends Parameter>, String> param =
        params -> params.isEmpty() ? "" : "/" + params.stream().map(p -> "{" + p.getName() + "}").collect(Collectors.joining("/"));

    public ODLRestconfPathPrinter(PathSegment path, boolean useModuleName) {
        this(path, useModuleName, false);
    }

    public ODLRestconfPathPrinter(PathSegment path, boolean useModuleName, boolean dropLastParams) {
        super(useModuleName, path, param, dropLastParams ? x -> "/" : param);
    }

    @Override
    public String segment() {
        return segment(paramPrinter, path.getModuleName(), path);

    }
}
