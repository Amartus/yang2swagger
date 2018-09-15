package com.mrv.yangtools.codegen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.junit.After;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;

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

    protected final Consumer<io.swagger.models.Path> singlePostOperation = p -> {
        assertEquals(1, p.getOperations().size());
        assertNotNull(p.getPost());
    };

    protected final Consumer<io.swagger.models.Path> correctRPCOperationModels = p -> {
        Operation post = p.getPost();
        if(post.getParameters() != null) {
            Optional<Parameter> body = post.getParameters().stream().filter(par -> "body".equals(par.getIn())).findFirst();
            if(body.isPresent()) {
                Property input = ((BodyParameter) body.get()).getSchema().getProperties().get("input");
                assertTrue(input instanceof RefProperty);
                assertNotNull("Incorrect structure in ", swagger.getDefinitions().get(((RefProperty)input).getSimpleRef()));
            }
        }

        Response response = post.getResponses().get("200");
        if(response != null) {
        	RefProperty schema = (RefProperty) response.getSchema();
            if(schema != null) {
                String ref = schema.getSimpleRef();
                Property output = swagger.getDefinitions().get(ref).getProperties().get("output");
                assertTrue(output instanceof RefProperty);
                assertNotNull("Incorrect structure in ",swagger.getDefinitions().get(((RefProperty)output).getSimpleRef()));

            }
        }
    };


    private SchemaContext ctxFor(Predicate<Path> cond) {
        try {
            return ContextHelper.getFromClasspath(cond);
        } catch (ReactorException e) {
            log.error("Cannot load context referencing {}", cond);
            log.error("Reason:", e);
            throw new IllegalArgumentException("Invalid precondition for context loader");
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
