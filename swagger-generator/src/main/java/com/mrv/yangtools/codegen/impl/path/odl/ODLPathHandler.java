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

import com.mrv.yangtools.codegen.DataObjectBuilder;
import com.mrv.yangtools.codegen.PathPrinter;
import com.mrv.yangtools.codegen.PathSegment;
import com.mrv.yangtools.codegen.TagGenerator;
import com.mrv.yangtools.codegen.impl.path.*;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import org.opendaylight.yangtools.yang.model.api.*;

import java.util.Set;

/**
 * REST path handler compliant with ODL RESTCONF https://wiki.opendaylight.org/view/OpenDaylight_Controller:MD-SAL:Restconf#Identifier_in_URI
 * @author damian.mrozowicz@amartus.com
 */
class ODLPathHandler extends AbstractPathHandler {

    private final String operational;

    ODLPathHandler(SchemaContext ctx, org.opendaylight.yangtools.yang.model.api.Module modules, Swagger target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
        super(ctx, modules, target, objBuilder, generators, fullCrud);
        operational = "/operational/";
        data = "/config/";
    }


    public ODLPathHandler useModuleName(boolean use) {
        this.useModuleName = use;
        return this;
    }

    @Override
    public void path(ContainerSchemaNode cN, PathSegment pathCtx) {
        final Path operationalPath = operationalOperations(cN, pathCtx);
        ODLRestconfPathPrinter operationalPathPrinter = new ODLRestconfPathPrinter(pathCtx, useModuleName);
        swagger.path(operational + operationalPathPrinter.path(), operationalPath);

        if (!pathCtx.isReadOnly()) {
            final Path configPath = operations(cN, pathCtx);
            ODLRestconfPathPrinter configPathPrinter = new ODLRestconfPathPrinter(pathCtx, useModuleName);
            swagger.path(data + configPathPrinter.path(), configPath);
        }
    }

    protected Path operationalOperations(DataSchemaNode node, PathSegment pathCtx) {
        final Path path = new Path();
        path.get(new GetOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags(pathCtx)));
        return path;
    }

    @Override
    public void path(ListSchemaNode lN, PathSegment pathCtx) {
        final Path operationalPath = operationalOperations(lN, pathCtx);
        ODLRestconfPathPrinter operationalPathPrinter = new ODLRestconfPathPrinter(pathCtx, useModuleName);
        swagger.path(operational + operationalPathPrinter.path(), operationalPath);

        if (!pathCtx.isReadOnly()) {
            final Path configPath = operations(lN, pathCtx);
            ODLRestconfPathPrinter configPathPrinter = new ODLRestconfPathPrinter(pathCtx, useModuleName);
            swagger.path(data + configPathPrinter.path(), configPath);

            if(fullCrud) {
                //referencing list path
                final Path list = new Path();
                list.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, true).execute(lN));


                ODLRestconfPathPrinter postPrinter = new ODLRestconfPathPrinter(pathCtx, useModuleName, true);
                swagger.path(data + postPrinter.path(), list);
            }
        }
    }

    @Override
    protected PathPrinter getPrinter(PathSegment pathCtx) {
        return new ODLRestconfPathPrinter(pathCtx, useModuleName);
    }

    @Override
    protected boolean generateModifyOperations(PathSegment pathCtx) {
        return fullCrud;
    }
}
