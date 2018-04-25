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

import java.util.List;
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
        //TODO pluggable PathPrinter
        RestconfPathPrinter printer = new RestconfPathPrinter(pathCtx, useModuleName);

        swagger.path(data + printer.path(), path);
    }

    protected Path operations(DataSchemaNode node, PathSegment pathCtx) {
        final Path path = new Path();
        List<String> tags = tags(pathCtx);
        tags.add(module.getName());

        path.get(new GetOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        if(fullCrud && !pathCtx.isReadOnly()) {
            path.put(new PutOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
            path.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, false).execute(node).tags(tags));
            path.delete(new DeleteOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        }

        return path;
    }

    @Override
    public void path(ListSchemaNode lN, PathSegment pathCtx) {
        final Path path = operations(lN, pathCtx);

        List<String> tags = tags(pathCtx);
        tags.add(module.getName());

        RestconfPathPrinter printer = new RestconfPathPrinter(pathCtx, useModuleName);
        swagger.path(data + printer.path(), path);

        //yes I know it can be written in previous 'if statement' but at some point it is to be refactored
        if(!fullCrud || pathCtx.isReadOnly()) return;


        //referencing list path
        final Path list = new Path();
        list.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, true).execute(lN));


        RestconfPathPrinter postPrinter = new RestconfPathPrinter(pathCtx, useModuleName, true);
        swagger.path(data + postPrinter.path(), list);
    }

    @Override
    protected PathPrinter getPrinter(PathSegment pathCtx) {
        return new RestconfPathPrinter(pathCtx, useModuleName);
    }




}
