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

import com.mrv.yangtools.codegen.DataObjectBuilder;
import com.mrv.yangtools.codegen.PathPrinter;
import com.mrv.yangtools.codegen.PathSegment;
import com.mrv.yangtools.codegen.TagGenerator;
import com.mrv.yangtools.codegen.impl.path.*;
import io.swagger.models.*;
import org.opendaylight.yangtools.yang.model.api.*;

import java.util.Set;

/**
 * REST path handler compliant with RESTCONF spec RFC 8040
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
class PathHandler extends AbstractPathHandler {

    PathHandler(SchemaContext ctx, org.opendaylight.yangtools.yang.model.api.Module modules, Swagger target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
        super(ctx, modules, target, objBuilder, generators, fullCrud);
    }

    public PathHandler useModuleName(boolean use) {
        this.useModuleName = use;
        return this;
    }


    @Override
    public void path(ContainerSchemaNode cN, PathSegment pathCtx) {
        final Path path = operations(cN, pathCtx);
        RestconfPathPrinter printer = new RestconfPathPrinter(pathCtx, useModuleName);

        swagger.path(data + printer.path(), path);
    }

    @Override
    public void path(ListSchemaNode lN, PathSegment pathCtx) {
        final Path path = operations(lN, pathCtx);

        RestconfPathPrinter printer = new RestconfPathPrinter(pathCtx, useModuleName);
        swagger.path(data + printer.path(), path);

        if(!fullCrud || pathCtx.isReadOnly()) return;

        //referencing list path
        final Path list = new Path();
        list.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, true).execute(lN).tags(tags(pathCtx)));


        RestconfPathPrinter postPrinter = new RestconfPathPrinter(pathCtx, useModuleName, true);
        swagger.path(data + postPrinter.path(), list);
    }

    @Override
    protected PathPrinter getPrinter(PathSegment pathCtx) {
        return new RestconfPathPrinter(pathCtx, useModuleName);
    }

    @Override
    protected boolean generateModifyOperations(PathSegment pathCtx) {
        return fullCrud && !pathCtx.isReadOnly();
    }


}
