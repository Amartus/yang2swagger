/*
 *   Copyright (c) 2024.  MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Christopher Murch <cmurch@mrv.com>
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl.path;

import com.mrv.yangtools.codegen.*;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.data.util.ContainerSchemaNodes;
import org.opendaylight.yangtools.yang.model.api.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractPathHandler implements PathHandler {
    protected final Swagger swagger;
    protected final EffectiveModelContext ctx;
    protected final org.opendaylight.yangtools.yang.model.api.Module module;
    protected boolean useModuleName;
    protected String data;
    protected String operations;
    protected final DataObjectBuilder dataObjectBuilder;
    protected final Set<TagGenerator> tagGenerators;
    protected final  boolean fullCrud;

    protected AbstractPathHandler(EffectiveModelContext ctx, org.opendaylight.yangtools.yang.model.api.Module modules, Swagger target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
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
    public void path(RpcDefinition rpc, PathSegment pathCtx) {
        InputSchemaNode input = rpc.getInput();
        OutputSchemaNode output = rpc.getOutput();
        ContainerLike root = ContainerSchemaNodes.forRPC(rpc);
        
        input = input.getChildNodes().isEmpty() ? null : input;
        output = output.getChildNodes().isEmpty() ? null : output;
    	
        PathPrinter printer = getPrinter(pathCtx);

        Operation post = defaultOperation(pathCtx);

        post.tag(module.getName());
        if(input != null) {
            dataObjectBuilder.addModel(input);

            ModelImpl inputModel = new ModelImpl().type(ModelImpl.OBJECT);
            inputModel.addProperty("input", new RefProperty(dataObjectBuilder.getDefinitionRef(input)));

            post.summary("operates on " + dataObjectBuilder.getName(root));
            post.description("operates on " + dataObjectBuilder.getName(root));
            post.parameter(new BodyParameter()
                    .name(dataObjectBuilder.getName(input) + ".body-param")
                    .schema(inputModel)
                    .description(input.getDescription().orElse(null))
            );
        }

        if(output != null) {
            var model = new ModelImpl().type(ModelImpl.OBJECT);
            model.addProperty("output", new RefProperty(dataObjectBuilder.getDefinitionRef(output)));

            dataObjectBuilder.addModel(output);
            post.response(200, new Response()
                    .responseSchema(model)
                    .description(output.getDescription().orElse("Correct response")));
        }
        post.response(201, new Response().description("No response")); //no output body
        swagger.path(operations + printer.path(), new Path().post(post));
    }

    protected abstract PathPrinter getPrinter(PathSegment pathCtx);


    protected abstract boolean generateModifyOperations(PathSegment pathCtx);

    protected Path operations(DataSchemaNode node, PathSegment pathCtx) {
        final Path path = new Path();
        List<String> tags = tags(pathCtx);

        path.get(new GetOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        if(generateModifyOperations(pathCtx)) {
            path.put(new PutOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
            if(!pathCtx.forList()) {
                path.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, false).execute(node).tags(tags));
            }
            path.delete(new DeleteOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        }

        return path;
    }

    private Operation defaultOperation(PathSegment pathCtx) {
        final Operation operation = new Operation();
        operation.response(400, new Response().description("Internal error"));
        operation.setParameters(pathCtx.params());
        return operation;
    }

    protected List<String> tags(PathSegment pathCtx) {
        List<String> tags = new ArrayList<>(tagGenerators.stream().flatMap(g -> g.tags(pathCtx).stream())
                .collect(Collectors.toSet()));
        Collections.sort(tags);
        String moduleName = pathCtx.stream().map(PathSegment::getModuleName).filter(Objects::nonNull).findFirst().orElse(module.getName());
        tags.add(moduleName);
        return tags;
    }
}
