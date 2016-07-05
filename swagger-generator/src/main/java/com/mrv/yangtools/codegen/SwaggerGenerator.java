package com.mrv.yangtools.codegen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.codegen.impl.*;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.ObjectProperty;
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
 *
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGenerator {
    private static final Logger log = LoggerFactory.getLogger(SwaggerGenerator.class);
    private final SchemaContext ctx;
    private final Set<Module> modules;
    private final Swagger target;
    private final DataObjectsBuilder dataObjectsBuilder;
    private ObjectMapper mapper;


    private Set<Elements> toGenerate;


    public enum Format { YAML, JSON }
    public enum Elements {
        DATA, RCP
    }

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
        dataObjectsBuilder = new DataObjectsBuilder(ctx);

        //no exposed swagger API
        target.info(new Info());

        //setting defaults
        this
            .host("localhost:8080")
            .basePath("/restconf")
            .consumes("application/json")
            .produces("application/json")
            .version("1.0.0-SNAPSHOT")
            .elements(Elements.DATA, Elements.RCP)
            .format(Format.YAML);
    }

    /**
     * Define version for generated swagger
     * @param version of seager interface
     * @return itself
     */
    private SwaggerGenerator version(String version) {
        target.getInfo().version(version);
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
     * Run Swagger generation
     * @param writer target
     * @throws IOException
     */
    public void generate(Writer writer) throws IOException {
        if(writer == null) throw new NullPointerException();

        Swagger target = generate();

        mapper.writeValue(writer, target);
    }

    protected Swagger generate() throws IOException {

        ArrayList<String> mNames = new ArrayList<>();

        modules.stream().
                forEach(m -> {
            mNames.add(m.getName());
            dataObjectsBuilder.processModule(m);

        });

        modules.stream().
                forEach(m -> {
                    new ModuleGenerator(m).generate();
                });

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

        private ModuleGenerator(Module module) {
            this.module = module;


        }

        void generate() {
            if(toGenerate.contains(Elements.DATA)) {
                pathCtx = new PathSegment(ctx)
                        .withName("/data")
                        .withModule(module.getName());
                module.getChildNodes().forEach(this::generate);
            }

            if(toGenerate.contains(Elements.RCP)) {
                pathCtx = new PathSegment(ctx)
                        .withName("/operations")
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

            addPath(input, output);

            pathCtx = pathCtx.drop();
        }

        private void generate(DataSchemaNode node) {

            if(node instanceof ContainerSchemaNode) {
                log.info("procesing container statement {}", node.getQName().getLocalName() );
                final ContainerSchemaNode cN = (ContainerSchemaNode) node;

                pathCtx = new PathSegment(pathCtx)
                        .withName(cN.getQName().getLocalName())
                        .withModule(module.getName())
                        .asReadOnly(!cN.isConfiguration());

                addPath(cN);

                cN.getChildNodes().forEach(this::generate);
                target.addDefinition(dataObjectsBuilder.getName(cN), dataObjectsBuilder.build(cN));

                pathCtx = pathCtx.drop();
            } else if(node instanceof ListSchemaNode) {
                log.info("processing list statement {}", node.getQName().getLocalName() );
                final ListSchemaNode lN = (ListSchemaNode) node;

                pathCtx = new PathSegment(pathCtx)
                        .withName(lN.getQName().getLocalName())
                        .withModule(module.getName())
                        .asReadOnly(!lN.isConfiguration())
                        .withListNode(lN);

                addPath(lN);
                lN.getChildNodes().forEach(this::generate);
                target.addDefinition(dataObjectsBuilder.getName(lN), dataObjectsBuilder.build(lN));

                pathCtx = pathCtx.drop();
            } else if (node instanceof ChoiceSchemaNode) {
                //choice node and cases are invisible from the perspective of generating path
                log.info("inlining choice statement {}", node.getQName().getLocalName() );
                ((ChoiceSchemaNode) node).getCases().stream()
                        .flatMap(_case -> _case.getChildNodes().stream()).forEach(this::generate);
            }
        }

        private Operation defaultOperation() {
            final Operation operation = new Operation();
            operation.response(400, new Response().description("Internal error"));
            operation.setParameters(pathCtx.params());
            return operation;
        }


        protected Operation getOp(DataSchemaNode node) {
            final Operation get = defaultOperation();
            get.description("returns " + dataObjectsBuilder.getName(node));
            get.response(200, new Response()
                    .schema(new RefProperty(dataObjectsBuilder.getDefinitionId(node)))
                    .description(dataObjectsBuilder.getName(node)));
            return get;
        }


        /**
         * Add path for rcp
         * @param input optional rcp input
         * @param output optional rcp output
         */
        private void addPath(ContainerSchemaNode input, ContainerSchemaNode output) {

            Operation post = defaultOperation();
            if(input != null) {
                final Model definition = dataObjectsBuilder.build(input);
                post.parameter(new BodyParameter()
                        .name("input")
                        .schema(definition)
                        .description(input.getDescription())
                );
            }

            if(output != null) {
                String description = output.getDescription();
                if(description == null) {
                    description = "Correct response";
                }
                post.response(200, new Response()
                        .schema(new ObjectProperty(dataObjectsBuilder.build(output).getProperties()))
                        .description(description));
            }

            post.response(201, new Response().description("No response")); //no output body

            Restconf14PathPrinter printer = new Restconf14PathPrinter(pathCtx, false);
            target.path(printer.path(), new Path().post(post));
        }

        private void addPath(ListSchemaNode lN) {
            final Path path = new Path();
            path.get(new GetOperationGenerator(pathCtx, dataObjectsBuilder).execute(lN));
            if(!pathCtx.isReadOnly()) {
                path.put(new PutOperationGenerator(pathCtx, dataObjectsBuilder).execute(lN));
                path.post(new PostOperationGenerator(pathCtx, dataObjectsBuilder, false).execute(lN));
                path.delete(new DeleteOperationGenerator(pathCtx, dataObjectsBuilder).execute(lN));
            }
            Restconf14PathPrinter printer = new Restconf14PathPrinter(pathCtx, false);
            target.path(printer.path(), path);



            //yes I know it can be written in previous 'if statement' but at some point it is to be refactored
            if(pathCtx.isReadOnly()) return;


            //add list path
            final Path list = new Path();
            list.post(new PostOperationGenerator(pathCtx, dataObjectsBuilder, true).execute(lN));


            Restconf14PathPrinter postPrinter = new Restconf14PathPrinter(pathCtx, false, true);
            target.path(postPrinter.path(), list);

        }

        private void addPath(ContainerSchemaNode cN) {
            final Path path = new Path();
            path.get(new GetOperationGenerator(pathCtx, dataObjectsBuilder).execute(cN));
            if(!pathCtx.isReadOnly()) {
                path.put(new PutOperationGenerator(pathCtx, dataObjectsBuilder).execute(cN));
                path.post(new PostOperationGenerator(pathCtx, dataObjectsBuilder, false).execute(cN));
                path.delete(new DeleteOperationGenerator(pathCtx, dataObjectsBuilder).execute(cN));
            }
            Restconf14PathPrinter printer = new Restconf14PathPrinter(pathCtx, false);

            target.path(printer.path(), path);
        }
    }
}
