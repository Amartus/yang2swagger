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

package com.mrv.yangtools.codegen.impl.path.rfc8040;

import com.mrv.yangtools.codegen.PathPrinter;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.parameters.Parameter;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link PathPrinter} compliant with https://tools.ietf.org/html/rfc8040#section-3.3
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class RestconfPathPrinter extends PathPrinter {

    private static final Function<Collection<? extends Parameter>, String> param =
            params -> params.isEmpty() ? "" :
                    "=" + params.stream().map(p -> "{" + p.getName() + "}").collect(Collectors.joining(","));

    public RestconfPathPrinter(PathSegment path, boolean useModuleName) {
        this(path, useModuleName, false);
    }

    public RestconfPathPrinter(PathSegment path, boolean useModuleName, boolean dropLastParams) {
        super(useModuleName, path, param, dropLastParams ? x -> "/" : param);
    }

    @Override
    public String segment() {
        return segment(paramPrinter, path.getModuleName(), path);

    }
}
