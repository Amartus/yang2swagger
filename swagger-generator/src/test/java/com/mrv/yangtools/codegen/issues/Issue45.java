package com.mrv.yangtools.codegen.issues;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.codegen.AbstractItTest;
import com.mrv.yangtools.codegen.MountPointMappings;
import com.mrv.yangtools.codegen.MountPointTarget;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class Issue45 extends AbstractItTest {

    @Test
    public void testSpecificTypeMounting() throws IOException, ReactorException {
        runSwaggerGeneratorWithMountMappings(Arrays.asList("entry-type-1:content", "entry-type-2:content"));

        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(writer, swagger);

        validateMountPointMapping();
        validateActionMapping();
    }

    @Test
    public void testModuleMounting() throws IOException, ReactorException {

        runSwaggerGeneratorWithMountMappings(Arrays.asList("entry-type-1", "entry-type-2"));

        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(writer, swagger);

        validateMountPointMapping();
        validateActionMapping();
    }

    @Test
    public void testInvalidModuleMounting() throws IOException, ReactorException {

        try {
            runSwaggerGeneratorWithMountMappings(Arrays.asList("invalid-module"));
            Assert.fail("Expected IllegalArgumentException for invalid module mapping");
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("invalid-module does not exist"));
        }
    }

    private void validateActionMapping() {
        String actionPath = "/operations/list-manager:list-entry={name}/list-entry-action";
        assertTrue("Missing action path in swagger: " + actionPath, swagger.getPaths().containsKey(actionPath));
        Assert.assertNotNull("Action should be exposed as POST", swagger.getPaths().get(actionPath).getPost());
    }

    private void validateMountPointMapping() {
        ComposedModel specificConfig = (ComposedModel) swagger.getDefinitions().get("list.manager.listentry.SpecificConfig");
        List<String> refs = specificConfig.getAllOf().stream()
                .map(m -> ((RefModel) m).getSimpleRef())
                .collect(Collectors.toList());
        assertTrue(refs.contains("entry.type._1.Content"));
        assertTrue(refs.contains("entry.type._2.Content"));

        String rpcPath = "/data/list-manager:list-entry={name}/specific-config/entry-type-1:entry-type-1-operation";
        assertTrue("Missing RPC mountpoint path: " + rpcPath, swagger.getPaths().containsKey(rpcPath));
        Assert.assertNotNull("RPC operations should be a POST: " + rpcPath, swagger.getPaths().get(rpcPath).getPost());

        assertTrue("Mounted operation should have entry-type-1 tag assigned", swagger.getPaths().get(rpcPath).getPost().getTags().contains("entry-type-1"));
        List<Parameter> parameters = swagger.getPaths().get(rpcPath).getPost().getParameters();
        Assert.assertNotNull("Mounted RPC should define POST parameters: " + rpcPath, parameters);

        BodyParameter bodyParameter = parameters.stream()
                .filter(p -> p instanceof BodyParameter && "body".equals(p.getIn()))
                .map(p -> (BodyParameter) p)
                .findFirst()
                .orElse(null);

        Assert.assertNotNull("Mounted RPC should define body payload parameter: " + rpcPath, bodyParameter);
        Assert.assertEquals("body", bodyParameter.getIn());
        Assert.assertEquals("entry.type._1.entrytype1operation.Input.body-param", bodyParameter.getName());
        Assert.assertFalse("Body payload parameter should be optional", Boolean.TRUE.equals(bodyParameter.getRequired()));

        Model bodySchema = bodyParameter.getSchema();
        Assert.assertNotNull("Body parameter should expose schema", bodySchema);
        Assert.assertTrue("Body schema should be an object model", bodySchema instanceof ModelImpl);
        Assert.assertEquals("object", ((ModelImpl) bodySchema).getType());
        Assert.assertNotNull("Body schema should expose properties", bodySchema.getProperties());

        Property inputProperty = bodySchema.getProperties().get("input");
        Assert.assertNotNull("Body schema should expose 'input' property", inputProperty);
        Assert.assertTrue("Body schema input property should be a ref", inputProperty instanceof RefProperty);

        RefProperty inputRef = (RefProperty) inputProperty;
        Assert.assertEquals("#/definitions/entry.type._1.entrytype1operation.Input", inputRef.get$ref());
        Assert.assertEquals("#/definitions/entry.type._1.entrytype1operation.Input", inputRef.getOriginalRef());

        String mountedType2Path = "/data/list-manager:list-entry={name}/specific-config/entry-type-2:type-2";
        assertTrue("Missing mounted data path for entry-type-2: " + mountedType2Path, swagger.getPaths().containsKey(mountedType2Path));
        Assert.assertNotNull("Mounted entry-type-2 should expose GET", swagger.getPaths().get(mountedType2Path).getGet());
        Assert.assertNotNull("Mounted entry-type-2 should expose POST", swagger.getPaths().get(mountedType2Path).getPost());
        Assert.assertNotNull("Mounted entry-type-2 should expose PUT", swagger.getPaths().get(mountedType2Path).getPut());
        Assert.assertNotNull("Mounted entry-type-2 should expose DELETE", swagger.getPaths().get(mountedType2Path).getDelete());
        assertTrue("Mounted entry-type-2 should keep module tag", swagger.getPaths().get(mountedType2Path).getGet().getTags().contains("entry-type-2"));

        String globalType2Path = "/data/entry-type-2:type-2";
        Assert.assertFalse("entry-type-2 should not be generated globally when excluded from modulesToGenerate", swagger.getPaths().containsKey(globalType2Path));
    }

    private void runSwaggerGeneratorWithMountMappings(List<String> listEntryData) throws ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");
        final EffectiveModelContext context = buildEffectiveModelContext(
                Paths.get("src","test","resources","bug_45").toString(),
                p -> matcher.matches(p.getFileName()));

        Collection<? extends Module> modulesToGenerate = context.getModules().stream()
                .filter(module -> module.getName().equals("list-manager")
                        || module.getName().equals("entry-type-1"))
                        //|| module.getName().equals("entry-type-2"))
                .collect(Collectors.toList());

        SwaggerGenerator generator = new SwaggerGenerator(context, modulesToGenerate).defaultConfig()
                .pathHandler(new com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder().useModuleName());
        Map<String, List<MountPointTarget>> mappingMap = new HashMap<>();
        mappingMap.put("entry-type-specific-data", listEntryData.stream()
                .map(MountPointTarget::parse)
                .collect(Collectors.toList()));
        generator.yangmntMappings(new MountPointMappings(mappingMap));
        swagger = generator.generate();
    }

    private EffectiveModelContext buildEffectiveModelContext(String dir, Predicate<Path> accept)
            throws ReactorException {
        return ContextHelper.getFromDir(Stream.of(FileSystems.getDefault().getPath(dir)), accept);
    }
}
