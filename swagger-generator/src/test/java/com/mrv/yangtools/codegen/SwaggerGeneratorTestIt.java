package com.mrv.yangtools.codegen;

import com.mrv.yangtools.test.utils.ContextUtils;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import org.hamcrest.CoreMatchers;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGeneratorTestIt {

    private static final Logger log = LoggerFactory.getLogger(SwaggerGeneratorTestIt.class);


    @org.junit.Test
    public void testGenerateSimpleModule() throws Exception {
        SchemaContext ctx = ContextUtils.getFromClasspath(p -> p.getFileName().toString().equals("simplest.yang"));

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        Swagger swagger = generator.generate();

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2"
        )), defNames);

        assertEquals(new HashSet<>(Arrays.asList(
                "SimpleRoot", "Children1", "Children2"
        )), defNames);

        assertThat(swagger.getPaths().keySet(), CoreMatchers.hasItem("/data/simple-root/children1={id}/children2={simplest-id}/"));

        if(log.isDebugEnabled()) {
            final StringWriter result = new StringWriter();
            generator.generate(result);
            log.debug("generated:\n" + result.toString());
        }

    }

    @org.junit.Test
    public void testGenerateReadOnlyModule() throws Exception {

        //having
        SchemaContext ctx = ContextUtils.getFromClasspath(p -> p.getFileName().toString().equals("read-only.yang"));

        final Consumer<Path> onlyGetOperationExists = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getGet());
        };

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        Swagger swagger = generator.generate();

        //then
        // for read only operations only one get operation
        swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("c2")).map(Map.Entry::getValue)
                .forEach(onlyGetOperationExists);
    }

    @org.junit.Test
    public void testGenerateGroupingsModule() throws Exception {
        SchemaContext ctx = ContextUtils.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));

        //when
        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules());
        Swagger swagger = generator.generate();

        //then
        assertEquals(2, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-cc")).count());

    }


}