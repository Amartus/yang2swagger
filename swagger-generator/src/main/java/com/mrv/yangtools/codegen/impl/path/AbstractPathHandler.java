package com.mrv.yangtools.codegen.impl.path;

import com.mrv.yangtools.codegen.*;
import com.mrv.yangtools.codegen.impl.RpcContainerSchemaNode;

import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;

import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractPathHandler implements PathHandler {
    protected final Swagger swagger;
    protected final SchemaContext ctx;
    protected final org.opendaylight.yangtools.yang.model.api.Module module;
    protected boolean useModuleName;
    protected String data;
    protected String operations;
    protected final DataObjectBuilder dataObjectBuilder;
    protected final Set<TagGenerator> tagGenerators;
    protected final  boolean fullCrud;

    protected AbstractPathHandler(SchemaContext ctx, org.opendaylight.yangtools.yang.model.api.Module modules, Swagger target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
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
        ContainerSchemaNode input = rpc.getInput();
        ContainerSchemaNode output = rpc.getOutput();
        ContainerSchemaNode root = new RpcContainerSchemaNode(rpc);
        
        input = input.getChildNodes().isEmpty() ? null : input;
        output = output.getChildNodes().isEmpty() ? null : output;
    	
        PathPrinter printer = getPrinter(pathCtx);

        Operation post = defaultOperation(pathCtx);

        post.tag(module.getName());
        if(input != null) {
            dataObjectBuilder.addModel(input);

            ModelImpl inputModel = new ModelImpl();
            inputModel.addProperty("input", new RefProperty(dataObjectBuilder.getDefinitionId(input)));

            post.summary("operates on " + dataObjectBuilder.getName(root));
            post.description("operates on " + dataObjectBuilder.getName(root));
            post.parameter(new BodyParameter()
                    .name(dataObjectBuilder.getName(input) + ".body-param")
                    .schema(inputModel)
                    .description(input.getDescription())
            );
        }

        if(output != null) {
            String description = output.getDescription();
            if(description == null) {
                description = "Correct response";
            }

            RefProperty refProperty = new RefProperty();
            refProperty.set$ref(dataObjectBuilder.getDefinitionId(root));
            
            dataObjectBuilder.addModel(root);
            post.response(200, new Response()
                    .schema(refProperty)
                    .description(description));
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
        String moduleName = pathCtx.stream().filter(p -> p.getModuleName() != null).map(PathSegment::getModuleName).findFirst().orElse(module.getName());
        tags.add(moduleName);
        return tags;
    }
}
