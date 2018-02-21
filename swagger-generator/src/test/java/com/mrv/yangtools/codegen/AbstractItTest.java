package com.mrv.yangtools.codegen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.properties.Property;
import org.junit.After;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractItTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractItTest.class);

    protected Swagger swagger;

    @After
    public void printSwagger() throws IOException {
        if(log.isDebugEnabled() && swagger != null) {
            StringWriter writer = new StringWriter();
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.writeValue(writer, swagger);
            log.debug(writer.toString());
        }
    }


    private SchemaContext ctxFor(Predicate<Path> cond) {
        try {
            return ContextHelper.getFromClasspath(cond);
        } catch (ReactorException e) {
            log.error("Cannot load context using {}", cond);
            throw new IllegalArgumentException("Invalid precodintion for context loader");
        }
    }

    protected void swaggerFor(String fileName) {
        swaggerFor(fileName, null);
    }

    protected void swaggerFor(String fileName, Consumer<SwaggerGenerator> extraConfig) {
        swaggerFor(p -> p.getFileName().toString().equals(fileName), extraConfig);
    }

    protected void swaggerFor(Predicate<Path> cond) {
        swaggerFor(cond, null);
    }

    protected void swaggerFor(Predicate<Path> cond, Consumer<SwaggerGenerator> extraConfig) throws IllegalArgumentException {
        SchemaContext ctx = ctxFor(cond);

        SwaggerGenerator generator = new SwaggerGenerator(ctx, ctx.getModules()).defaultConfig();
        if(extraConfig != null) {
            extraConfig.accept(generator);
        }
        swagger = generator.generate();
    }

    protected void checkLeafrefAreFollowed(String modelName, String propertyName, String type) {
        Model model = swagger.getDefinitions().get(modelName);
        Property parentId = model.getProperties().get(propertyName);
        assertEquals(type, parentId.getType());
        assertFalse(parentId.getVendorExtensions().isEmpty());
        assertTrue(parentId.getVendorExtensions().containsKey("x-path"));
    }
}
