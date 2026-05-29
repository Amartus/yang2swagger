package com.mrv.yangtools.codegen.issues;

import com.mrv.yangtools.codegen.AbstractItTest;
import com.mrv.yangtools.codegen.MountPointMappings;
import com.mrv.yangtools.codegen.MountPointTarget;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.impl.path.AbstractPathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.postprocessor.Rfc4080PayloadWrapper;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Operation;
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

        validateMountPointMapping();
        validateActionMapping();
    }

    @Test
    public void testModuleMounting() throws IOException, ReactorException {

        runSwaggerGeneratorWithMountMappings(Arrays.asList("entry-type-1", "entry-type-2"));

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
        assertTrue(refs.contains("entry.type._1.content.Type1Wrapper"));
        assertTrue(refs.contains("entry.type._2.content.Type2Wrapper"));
        Assert.assertFalse("entry.type._1.Content should be removed as unused after mount-point processing",
                swagger.getDefinitions().containsKey("entry.type._1.Content"));
        Assert.assertFalse("entry.type._2.Content should be removed as unused after mount-point processing",
                swagger.getDefinitions().containsKey("entry.type._2.Content"));

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

    /**
     * issue #74: with split config/state enabled the mounted write operations and the parallel
     * {@code <name>Config} composed definition must omit {@code config false} (state) data.
     * {@code entry-type-2}'s {@code type-2} container carries a {@code last-updated} state leaf.
     */
    @Test
    public void testMountedWriteModelsOmitStateWhenSplitConfigState() throws IOException, ReactorException {
        runSwaggerGeneratorWithMountMappings(Arrays.asList("entry-type-1", "entry-type-2"),
                g -> g.splitConfigState(true));

        // the isolated entry-type-2 generation produced a pruned write model for type-2
        Model writeType2 = swagger.getDefinitions().get("entry.type._2.content.Type2Config");
        Assert.assertNotNull("Expected a split write model for the mounted type-2 container", writeType2);
        assertTrue("Write model keeps config leaves", writeType2.getProperties().keySet().containsAll(Arrays.asList("a", "b", "c")));
        Assert.assertFalse("Write model must drop the config-false leaf",
                writeType2.getProperties().containsKey("last-updated"));

        // the read model still carries the state leaf
        Model readType2 = swagger.getDefinitions().get("entry.type._2.content.Type2");
        assertTrue("Read model keeps the state leaf", readType2.getProperties().containsKey("last-updated"));

        // mounted write operations reference the state-free wrapper, GET keeps the full one
        String mountedType2Path = "/data/list-manager:list-entry={name}/specific-config/entry-type-2:type-2";
        io.swagger.models.Path mounted = swagger.getPaths().get(mountedType2Path);
        Assert.assertNotNull("Missing mounted data path for entry-type-2: " + mountedType2Path, mounted);
        Assert.assertEquals("entry.type._2.content.Type2ConfigWrapper", writeBodyRef(mounted.getPut()));
        Assert.assertEquals("entry.type._2.content.Type2ConfigWrapper", writeBodyRef(mounted.getPost()));

        // the parallel composed write definition for the mount node references state-free content
        ComposedModel writeComposed = (ComposedModel) swagger.getDefinitions()
                .get("list.manager.listentry.SpecificConfigConfig");
        Assert.assertNotNull("Expected a parallel write definition for the mount node", writeComposed);
        List<String> writeRefs = writeComposed.getAllOf().stream()
                .filter(m -> m instanceof RefModel)
                .map(m -> ((RefModel) m).getSimpleRef())
                .collect(Collectors.toList());
        assertTrue("Mount write definition should attach the state-free type-2 wrapper",
                writeRefs.contains("entry.type._2.content.Type2ConfigWrapper"));
        Assert.assertFalse("Mount write definition must not attach the state-bearing read wrapper",
                writeRefs.contains("entry.type._2.content.Type2Wrapper"));
    }

    private static String writeBodyRef(Operation operation) {
        if(operation == null || operation.getParameters() == null) return null;
        return operation.getParameters().stream()
                .filter(p -> p instanceof BodyParameter)
                .map(p -> ((BodyParameter) p).getSchema())
                .filter(m -> m instanceof RefModel)
                .map(m -> ((RefModel) m).getSimpleRef())
                .findFirst().orElse(null);
    }

    private void runSwaggerGeneratorWithMountMappings(List<String> listEntryData) throws ReactorException {
        runSwaggerGeneratorWithMountMappings(listEntryData, g -> {});
    }

    private void runSwaggerGeneratorWithMountMappings(List<String> listEntryData,
                                                      java.util.function.Consumer<SwaggerGenerator> configurator)
            throws ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");
        final EffectiveModelContext context = buildEffectiveModelContext(
                Paths.get("src","test","resources","bug_45").toString(),
                p -> matcher.matches(p.getFileName()));

        Collection<? extends Module> modulesToGenerate = context.getModules().stream()
                .filter(module -> module.getName().equals("list-manager")
                        || module.getName().equals("entry-type-1"))
                .collect(Collectors.toList());

        AbstractPathHandlerBuilder pathHandler = new com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder();
        pathHandler.useModuleName();

        SwaggerGenerator generator = new SwaggerGenerator(context, modulesToGenerate).defaultConfig()
                .pathHandler(pathHandler);
        Map<String, List<MountPointTarget>> mappingMap = new HashMap<>();
        mappingMap.put("entry-type-specific-data", listEntryData.stream()
                .map(MountPointTarget::parse)
                .collect(Collectors.toList()));
        generator.appendPostProcessor(new Rfc4080PayloadWrapper());
        configurator.accept(generator);
        generator.yangmntMappings(new MountPointMappings(mappingMap));
        swagger = generator.generate();
    }

    private EffectiveModelContext buildEffectiveModelContext(String dir, Predicate<Path> accept)
            throws ReactorException {
        return ContextHelper.getFromDir(Stream.of(FileSystems.getDefault().getPath(dir)), accept);
    }
}
