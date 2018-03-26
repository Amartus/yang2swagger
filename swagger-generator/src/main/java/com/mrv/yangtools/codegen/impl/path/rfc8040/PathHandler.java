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
import com.mrv.yangtools.codegen.PathSegment;
import com.mrv.yangtools.codegen.TagGenerator;
import com.mrv.yangtools.codegen.impl.path.DeleteOperationGenerator;
import com.mrv.yangtools.codegen.impl.path.GetOperationGenerator;
import com.mrv.yangtools.codegen.impl.path.PostOperationGenerator;
import com.mrv.yangtools.codegen.impl.path.PutOperationGenerator;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST path handler compliant with RESTCONF spec RFC 8040
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
class PathHandler implements com.mrv.yangtools.codegen.PathHandler {
    private final Swagger swagger;
    private final SchemaContext ctx;

    private final String data;
    private final String operations;
    private final org.opendaylight.yangtools.yang.model.api.Module module;
    private final DataObjectBuilder dataObjectBuilder;
    private final Set<TagGenerator> tagGenerators;
    private final  boolean fullCrud;
    private boolean useModuleName;

    PathHandler(SchemaContext ctx, org.opendaylight.yangtools.yang.model.api.Module modules, Swagger target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
        this.swagger = target;
        this.ctx = ctx;
        this.module = modules;
        data = "/data/";
        operations = "/operations/";
        this.dataObjectBuilder = objBuilder;
        this.tagGenerators = generators;
        this.fullCrud = fullCrud;

        this.useModuleName = false;
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
    public void path(ContainerSchemaNode input, ContainerSchemaNode output, PathSegment pathCtx) {
        final RestconfPathPrinter printer = new RestconfPathPrinter(pathCtx, useModuleName);

        Operation post = defaultOperation(pathCtx);

        post.tag(module.getName());
        if(input != null) {
            dataObjectBuilder.addModel(input, "input");

            post.parameter(new BodyParameter()
                    .name("body-param")
                    .schema(new RefModel(dataObjectBuilder.getDefinitionId(input)))
                    .description(input.getDescription())
            );
        }

        if(output != null) {
            String description = output.getDescription();
            if(description == null) {
                description = "Correct response";
            }

            dataObjectBuilder.addModel(output, "output");
            post.response(200, new Response()
                    .schema(new RefProperty(dataObjectBuilder.getDefinitionId(output)))
                    .description(description));
        }
        post.response(201, new Response().description("No response")); //no output body
        swagger.path(operations + module.getName() + ':' + printer.path(), new Path().post(post));
    }

    private List<String> tags(PathSegment pathCtx) {
        List<String> tags = new ArrayList<>(tagGenerators.stream().flatMap(g -> g.tags(pathCtx).stream())
                .collect(Collectors.toSet()));
        Collections.sort(tags);
        return tags;
    }

    private Operation defaultOperation(PathSegment pathCtx) {
        final Operation operation = new Operation();
        operation.response(400, new Response().description("Internal error"));
        operation.setParameters(pathCtx.params());
        return operation;
    }
}
