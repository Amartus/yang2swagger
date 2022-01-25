/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */
package com.mrv.yangtools.codegen;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.mrv.yangtools.codegen.impl.AnnotatingTypeConverter;
import com.mrv.yangtools.codegen.impl.ModuleUtils;
import com.mrv.yangtools.codegen.impl.OptimizingDataObjectBuilder;
import com.mrv.yangtools.codegen.impl.UnpackingDataObjectsBuilder;
import com.mrv.yangtools.codegen.impl.postprocessor.ReplaceEmptyWithParent;

import io.swagger.models.Info;
import io.swagger.models.Swagger;

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
 * @author damian.mrozowicz@amartus.com
 */
public class IoCSwaggerGenerator {
    private static final Logger log = LoggerFactory.getLogger(IoCSwaggerGenerator.class);
    private final SchemaContext ctx;
    private final Set<org.opendaylight.yangtools.yang.model.api.Module> modules;
    private final Swagger target;
    private final Set<String> moduleNames;
    private final ModuleUtils moduleUtils;
    private Consumer<Swagger> postprocessor;
    private DataObjectBuilder dataObjectsBuilder;
    private ObjectMapper mapper;
    private int maxDepth = Integer.MAX_VALUE;


    private Set<Elements> toGenerate;
    private final AnnotatingTypeConverter converter;
    private PathHandlerBuilder pathHandlerBuilder;

    public IoCSwaggerGenerator defaultConfig() {
        //setting defaults
        this
                .host("localhost:8080")
                .basePath("/restconf")
                .consumes("application/json")
                .produces("application/json")
                .version("1.0.0-SNAPSHOT")
                .elements(Elements.DATA, Elements.RPC)
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
        RPC
    }

    public enum Strategy {optimizing, unpacking}

    /**
     * Preconfigure generator. By default it will genrate api for Data and RPC with JSon payloads only.
     * The api will be in YAML format. You might change default setting with config methods of the class
     * @param ctx context for generation
     * @param modulesToGenerate modules that will be transformed to swagger API
     */
    @Inject
    public IoCSwaggerGenerator(@Assisted SchemaContext ctx, @Assisted Set<Module> modulesToGenerate) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(modulesToGenerate);
        if(modulesToGenerate.isEmpty()) throw new IllegalStateException("No modules to generate has been specified");
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

        //default postprocessors
        postprocessor = new ReplaceEmptyWithParent();
    }

    /**
     * Define version for generated swagger
     * @param version of swagger interface
     * @return itself
     */
    public IoCSwaggerGenerator version(String version) {
        target.getInfo().version(version);
        return this;
    }

    /**
     * Add tag generator
     * @param generator to be added
     * @return this
     */
    public IoCSwaggerGenerator tagGenerator(TagGenerator generator) {
        pathHandlerBuilder.addTagGenerator(generator);
        return this;
    }

    public IoCSwaggerGenerator appendPostProcessor(Consumer<Swagger> swaggerPostprocessor) {
        Objects.requireNonNull(swaggerPostprocessor);
        postprocessor = postprocessor.andThen(swaggerPostprocessor);
        return this;
    }

    /**
     * Configure strategy
     * @param strategy to be used
     * @return this
     */
    public IoCSwaggerGenerator strategy(Strategy strategy) {
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

    @Inject
    public IoCSwaggerGenerator pathHandler(PathHandlerBuilder handlerBuilder) {
        Objects.requireNonNull(handlerBuilder);

        if(this.pathHandlerBuilder != null) {
            pathHandlerBuilder.getTagGenerators().forEach(handlerBuilder::addTagGenerator);
        }

        this.pathHandlerBuilder = handlerBuilder;
        return this;
    }

    /**
     * YANG elements that are taken into account during generation
     * @return this
     */
    public IoCSwaggerGenerator elements(Elements... elements) {
        toGenerate = new HashSet<>(Arrays.asList(elements));
        return this;
    }

    /**
     * Output format
     * @param f YAML or JSON
     * @return itself
     */
    public IoCSwaggerGenerator format(Format f) {
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
    public IoCSwaggerGenerator host(String host) {
        target.host(host);
        return this;
    }


    /**
     * Set base path
     * @param basePath '/restconf' by default
     * @return this
     */
    public IoCSwaggerGenerator basePath(String basePath) {
        target.basePath(basePath);
        return this;
    }

    /**
     * Add consumes type header for all methods
     * @param consumes type header
     * @return this
     */
    public IoCSwaggerGenerator consumes(String consumes) {
        Objects.requireNonNull(consumes);
        target.consumes(consumes);
        return this;
    }

    /**
     * Add produces type header for all methods
     * @param produces type header
     * @return this
     */
    public IoCSwaggerGenerator produces(String produces) {
        Objects.requireNonNull(produces);
        target.produces(produces);
        return this;
    }
    
    /**
     * Add max depth level during walk through module node tree
     * @param maxDepth to which paths should be generated
     * @return this
     */
    public IoCSwaggerGenerator maxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }    

    /**
     * Run Swagger generation for configured modules. Write result to target. The file format
     * depends on configured {@link IoCSwaggerGenerator.Format}
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
        ArrayList<String> mDescs = new ArrayList<>();

        if(ctx.getModules().isEmpty() || modules.isEmpty()) {
            log.info("No modules found to be transformed into swagger definition");
            return target;
        }

        log.info("Generating swagger for yang modules: {}",
                modules.stream().map(ModuleIdentifier::getName).collect(Collectors.joining(",","[", "]")));

        modules.forEach(m -> {
            mNames.add(m.getName());
            if(m.getDescription() != null && !m.getDescription().isEmpty()) {
                mDescs.add(m.getDescription());
            }
            dataObjectsBuilder.processModule(m);

        });
        //initialize plugable path handler
        pathHandlerBuilder.configure(ctx, target, dataObjectsBuilder);

        modules.forEach(m -> new ModuleGenerator(m).generate());

        // update info with module names and descriptions
        String modules = mNames.stream().collect(Collectors.joining(","));
        String descriptions = mDescs.stream().collect(Collectors.joining(","));
        target.getInfo()
                .description(descriptions)
                .title(modules + " API");

        postProcessSwagger(target);

        return target;
    }

    /**
     * Replace empty definitions with their parents.
     * Sort models (ref models first)
     * @param target to work on
     */
    protected void postProcessSwagger(Swagger target) {
        if(target.getDefinitions() == null || target.getDefinitions().isEmpty()) {
            log.warn("Generated swagger has no definitions");
            return;
        }
        postprocessor.accept(target);
    }

    private class ModuleGenerator {
        private final org.opendaylight.yangtools.yang.model.api.Module module;
        private PathSegment pathCtx;
        private PathHandler handler;

        private ModuleGenerator(org.opendaylight.yangtools.yang.model.api.Module module) {
            if(module == null) throw new NullPointerException("module is null");
            this.module = module;
            handler = pathHandlerBuilder.forModule(module);


        }

        void generate() {
            if(toGenerate.contains(Elements.DATA)) {
                pathCtx = new PathSegment(ctx)
                        .withModule(module.getName());
                module.getChildNodes().forEach(n -> generate(n, maxDepth));
            }

            if(toGenerate.contains(Elements.RPC)) {
                pathCtx = new PathSegment(ctx)
                        .withModule(module.getName());
                module.getRpcs().forEach(this::generate);
            }
        }

        private void generate(RpcDefinition rpc) {
            pathCtx = new PathSegment(pathCtx)
                        .withName(rpc.getQName().getLocalName())
                        .withModule(module.getName());

            handler.path(rpc, pathCtx);

            pathCtx = pathCtx.drop();
        }

        private void generate(DataSchemaNode node, final int depth) {
        	if(depth == 0) {
        		log.debug("Maxmium depth level reached, skipping {} and it's childs", node.getPath());
        		return;
        	}
        	
            if(!moduleNames.contains(moduleUtils.toModuleName(node))) {
                log.debug("skipping {} as it is from {} module", node.getPath(), moduleUtils.toModuleName(node));
                return;
            }

            if(node instanceof ContainerSchemaNode) {
                log.info("processing container statement {}", node.getQName().getLocalName() );
                final ContainerSchemaNode cN = (ContainerSchemaNode) node;

                pathCtx = new PathSegment(pathCtx)
                        .withName(cN.getQName().getLocalName())
                        .withModule(moduleUtils.toModuleName(node))
                        .asReadOnly(!cN.isConfiguration());

                handler.path(cN, pathCtx);
                cN.getChildNodes().forEach(n -> generate(n, depth-1));
                dataObjectsBuilder.addModel(cN);

                pathCtx = pathCtx.drop();
            } else if(node instanceof ListSchemaNode) {
                log.info("processing list statement {}", node.getQName().getLocalName() );
                final ListSchemaNode lN = (ListSchemaNode) node;

                pathCtx = new PathSegment(pathCtx)
                        .withName(lN.getQName().getLocalName())
                        .withModule(moduleUtils.toModuleName(node))
                        .asReadOnly(!lN.isConfiguration())
                        .withListNode(lN);

                handler.path(lN, pathCtx);
                lN.getChildNodes().forEach(n -> generate(n, depth-1));
                dataObjectsBuilder.addModel(lN);

                pathCtx = pathCtx.drop();
            } else if (node instanceof ChoiceSchemaNode) {
                //choice node and cases are invisible from the perspective of generating path
                log.info("inlining choice statement {}", node.getQName().getLocalName() );
                ((ChoiceSchemaNode) node).getCases().stream()
                        .flatMap(_case -> _case.getChildNodes().stream()).forEach(n -> generate(n, depth-1));
            }
        }
    }
}
