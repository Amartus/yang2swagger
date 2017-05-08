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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.codegen.impl.*;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * YANG to Swagger generator
 * Generates swagger definitions from yang modules. The output format is either YAML or JSon.
 * The generator can generate Swagger path definitions for all data nodes (currently <code>container</code>, <code>list</code>).
 * Generator swagger modeling concepts:
 * <ul>
 *     <li>container, list, leaf, leaf-list, enums</li> - in addition Swagger tags are build from module verbs (depending on{@link TagGenerator} configured)
 *     <li>groupings - depending on {@link Strategy} groupings are either inlined or define data models</li>
 *     <li>leaf-refs - leafrefs paths are mapped to Swagger extensions, leafrefs to attributes with type of the element they refer to</li>
 *     <li>augmentations</li>
 *     <li>config flag - for operational data only GET operations are generated</li>
 * </ul>
 *
 *
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGenerator {
    private static final Logger log = LoggerFactory.getLogger(SwaggerGenerator.class);
    private final SchemaContext ctx;
    private final Set<Module> modules;
    private final Swagger target;
    private final Set<String> moduleNames;
    private final ModuleUtils moduleUtils;
    private DataObjectBuilder dataObjectsBuilder;
    private ObjectMapper mapper;


    private Set<Elements> toGenerate;
    private final AnnotatingTypeConverter converter;
    private PathHandlerBuilder pathHandlerBuilder;

    public SwaggerGenerator defaultConfig() {
        //setting defaults
        this
                .host("localhost:8080")
                .basePath("/restconf")
                .consumes("application/json")
                .produces("application/json")
                .version("1.0.0-SNAPSHOT")
                .elements(Elements.DATA, Elements.RCP)
                .format(Format.YAML);
        return this;
    }


    public enum Format { YAML, JSON }
    public enum Elements {
        /**
         * to generate path for data model (containers and lists)
         */
        DATA,
        /**
         * to generate paths for RPC operations
         */
        RCP
    }

    public enum Strategy {optimizing, unpacking}

    /**
     * Preconfigure generator. By default it will genrate api for Data and RCP with JSon payloads only.
     * The api will be in YAML format. You might change default setting with config methods of the class
     * @param ctx context for generation
     * @param modulesToGenerate modules that will be transformed to swagger API
     */
    public SwaggerGenerator(SchemaContext ctx, Set<Module> modulesToGenerate) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(modulesToGenerate);
        this.ctx = ctx;
        this.modules = modulesToGenerate;
        target = new Swagger();
        converter = new AnnotatingTypeConverter(ctx);
        moduleUtils = new ModuleUtils(ctx);
        this.moduleNames = modulesToGenerate.stream().map(ModuleIdentifier::getName).collect(Collectors.toSet());
        //assign default strategy
        strategy(Strategy.optimizing);

        //no exposed swagger API
        target.info(new Info());

        pathHandlerBuilder = new com.mrv.yangtools.codegen.rfc8040.PathHandlerBuilder();
    }

    /**
     * Define version for generated swagger
     * @param version of swagger interface
     * @return itself
     */
    public SwaggerGenerator version(String version) {
        target.getInfo().version(version);
        return this;
    }

    /**
     * Add tag generator
     * @param generator to be added
     * @return this
     */
    public SwaggerGenerator tagGenerator(TagGenerator generator) {
        pathHandlerBuilder.addTagGenerator(generator);
        return this;
    }

    /**
     * Configure strategy
     * @param strategy to be used
     * @return this
     */
    public SwaggerGenerator strategy(Strategy strategy) {
        Objects.requireNonNull(strategy);

        switch (strategy) {
            case optimizing:
                this.dataObjectsBuilder = new OptimizingDataObjectBuilder(ctx, target, converter);
                break;
            default:
                this.dataObjectsBuilder = new UnpackingDataObjectsBuilder(ctx, target, converter);
        }
        return this;
    }

    public SwaggerGenerator pathHandler(PathHandlerBuilder handlerBuilder) {
        Objects.requireNonNull(handlerBuilder);
        this.pathHandlerBuilder = handlerBuilder;
        return this;
    }

    /**
     * YANG elements that are taken into account during generation
     * @return this
     */
    public SwaggerGenerator elements(Elements... elements) {
        toGenerate = new HashSet<>(Arrays.asList(elements));
        return this;
    }

    /**
     * Output format
     * @param f YAML or JSON
     * @return itself
     */
    public SwaggerGenerator format(Format f) {
        switch(f) {
            case YAML:
                mapper = new ObjectMapper(new YAMLFactory());
                break;
            case JSON:
            default:
                mapper = new ObjectMapper(new JsonFactory());
        }
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return this;
    }

    /**
     * Set host config for Swagger output
     * @param host general host to bind Swagger definition
     * @return this
     */
    public SwaggerGenerator host(String host) {
        target.host(host);
        return this;
    }


    /**
     * Set base path
     * @param basePath '/restconf' by default
     * @return this
     */
    public SwaggerGenerator basePath(String basePath) {
        target.basePath(basePath);
        return this;
    }

    /**
     * Add consumes type header for all methods
     * @param consumes type header
     * @return this
     */
    public SwaggerGenerator consumes(String consumes) {
        Objects.requireNonNull(consumes);
        target.consumes(consumes);
        return this;
    }

    /**
     * Add produces type header for all methods
     * @param produces type header
     * @return this
     */
    public SwaggerGenerator produces(String produces) {
        Objects.requireNonNull(produces);
        target.produces(produces);
        return this;
    }

    /**
     * Run Swagger generation for configured modules. Write result to target. The file format
     * depends on configured {@link SwaggerGenerator.Format}
     * @param target writer
     * @throws IOException when problem with writing
     */
    public void generate(Writer target) throws IOException {
        if(target == null) throw new NullPointerException();

        Swagger result = generate();

        mapper.writeValue(target, result);
    }

    /**
     * Run Swagger generation for configured modules.
     * @return Swagger model
     */
    public Swagger generate() {

        ArrayList<String> mNames = new ArrayList<>();

        modules.forEach(m -> {
            mNames.add(m.getName());
            dataObjectsBuilder.processModule(m);

        });
        //initialize plugable path handler
        pathHandlerBuilder.configure(ctx, target, dataObjectsBuilder);

        modules.forEach(m -> new ModuleGenerator(m).generate());

        // update info with module names
        String modules = mNames.stream().collect(Collectors.joining(","));
        target.getInfo()
                .description(modules + " API generated from yang definitions")
                .title(modules + " API");
        return target;
    }


    private class ModuleGenerator {
        private final Module module;
        private PathSegment pathCtx;
        private PathHandler handler;

        private ModuleGenerator(Module module) {
            if(module == null) throw new NullPointerException("module is null");
            this.module = module;
            handler = pathHandlerBuilder.forModule(module);


        }

        void generate() {
            if(toGenerate.contains(Elements.DATA)) {
                pathCtx = new PathSegment(ctx)
                        .withModule(module.getName());
                module.getChildNodes().forEach(this::generate);
            }

            if(toGenerate.contains(Elements.RCP)) {
                pathCtx = new PathSegment(ctx)
                        .withModule(module.getName());
                module.getRpcs().forEach(this::generate);
            }
        }

        private void generate(RpcDefinition rcp) {
            pathCtx = new PathSegment(pathCtx)
                        .withName(rcp.getQName().getLocalName())
                        .withModule(module.getName());

            ContainerSchemaNode input = rcp.getInput();
            ContainerSchemaNode output = rcp.getOutput();
            handler.path(input, output, pathCtx);

            pathCtx = pathCtx.drop();
        }

        private void generate(DataSchemaNode node) {
            if(!moduleNames.contains(moduleUtils.toModuleName(node))) {
                log.debug("skipping {} as it is from {} module", node.getPath(), moduleUtils.toModuleName(node));
                return;
            }

            if(node instanceof ContainerSchemaNode) {
                log.info("processing container statement {}", node.getQName().getLocalName() );
                final ContainerSchemaNode cN = (ContainerSchemaNode) node;

                pathCtx = new PathSegment(pathCtx)
                        .withName(cN.getQName().getLocalName())
                        .withModule(module.getName())
                        .asReadOnly(!cN.isConfiguration());

                handler.path(cN, pathCtx);
                cN.getChildNodes().forEach(this::generate);
                dataObjectsBuilder.addModel(cN);

                pathCtx = pathCtx.drop();
            } else if(node instanceof ListSchemaNode) {
                log.info("processing list statement {}", node.getQName().getLocalName() );
                final ListSchemaNode lN = (ListSchemaNode) node;

                pathCtx = new PathSegment(pathCtx)
                        .withName(lN.getQName().getLocalName())
                        .withModule(module.getName())
                        .asReadOnly(!lN.isConfiguration())
                        .withListNode(lN);

                handler.path(lN, pathCtx);
                lN.getChildNodes().forEach(this::generate);
                dataObjectsBuilder.addModel(lN);

                pathCtx = pathCtx.drop();
            } else if (node instanceof ChoiceSchemaNode) {
                //choice node and cases are invisible from the perspective of generating path
                log.info("inlining choice statement {}", node.getQName().getLocalName() );
                ((ChoiceSchemaNode) node).getCases().stream()
                        .flatMap(_case -> _case.getChildNodes().stream()).forEach(this::generate);
            }
        }
    }
}
