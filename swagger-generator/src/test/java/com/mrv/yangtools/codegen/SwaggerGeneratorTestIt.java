package com.mrv.yangtools.codegen;

import com.mrv.yangtools.test.utils.ContextUtils;
import io.swagger.models.Swagger;
import org.hamcrest.CoreMatchers;
import org.junit.BeforeClass;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGeneratorTestIt {

    private static final Logger log = LoggerFactory.getLogger(SwaggerGeneratorTestIt.class);

    private static SchemaContext ctx;

    @BeforeClass
    public static void  initCtx() throws ReactorException {
        ctx = ContextUtils.getFromClasspath(p ->  p.getFileName().toString().equals("simplest.yang"));
    }


    @org.junit.Test
    public void testGenerate() throws Exception {
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
}