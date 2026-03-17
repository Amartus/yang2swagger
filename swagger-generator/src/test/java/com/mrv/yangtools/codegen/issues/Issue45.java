package com.mrv.yangtools.codegen.issues;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mrv.yangtools.codegen.AbstractItTest;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.ComposedModel;
import io.swagger.models.RefModel;
import org.junit.Test;
import org.junit.Assert;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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

public class Issue45 extends AbstractItTest {

    @Test
    public void testSpecificTypeMounting() throws IOException, ReactorException {
        runSwaggerGeneratorWithMountMappings(Arrays.asList("entry-type-1:content", "entry-type-2:content"));

        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(writer, swagger);

        createSwaggerFile(writer, "swagger.yml");

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

        createSwaggerFile(writer, "swagger2.yml");

        validateMountPointMapping();
        validateActionMapping();
    }

    @Test
    public void testInvalidModuleMounting() throws IOException, ReactorException {

        try {
            runSwaggerGeneratorWithMountMappings(Arrays.asList("invalid-module"));
            Assert.fail("Expected IllegalArgumentException for invalid module mapping");
        } catch (IllegalArgumentException iae) {
            Assert.assertTrue(iae.getMessage().contains("invalid-module does not exist"));
        }

        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(writer, swagger);

        createSwaggerFile(writer, "swagger3.yml");
    }

    private void validateActionMapping() {
        String actionPath = "/operations/list-manager:list-entry={name}/list-entry-action";
        Assert.assertTrue("Missing action path in swagger: " + actionPath, swagger.getPaths().containsKey(actionPath));
        Assert.assertNotNull("Action should be exposed as POST", swagger.getPaths().get(actionPath).getPost());
    }

    private void validateMountPointMapping() {
        ComposedModel specificConfig = (ComposedModel) swagger.getDefinitions().get("list.manager.listentry.SpecificConfig");
        Assert.assertEquals("entry.type._1.Content", ((RefModel) specificConfig.getAllOf().get(0)).getSimpleRef());
        Assert.assertEquals("entry.type._2.Content", ((RefModel) specificConfig.getAllOf().get(1)).getSimpleRef());
    }

    private void runSwaggerGeneratorWithMountMappings(List<String> listEntryData) throws ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");
        final EffectiveModelContext context = buildEffectiveModelContext(
                Paths.get("src","test","resources","bug_45").toString(),
                p -> matcher.matches(p.getFileName()));

        Collection<? extends Module> modulesToGenerate = context.getModules().stream()
                .filter(module -> module.getName().equals("list-manager")
                        || module.getName().equals("entry-type-1")
                        || module.getName().equals("entry-type-2"))
                .collect(Collectors.toList());

        SwaggerGenerator generator = new SwaggerGenerator(context, modulesToGenerate).defaultConfig();
        Map<String, List<String>> mapping = new HashMap<>();
        mapping.put("entry-type-specific-data", listEntryData);
        generator.yangmntMappings(mapping);
        swagger = generator.generate();
    }

    private EffectiveModelContext buildEffectiveModelContext(String dir, Predicate<Path> accept)
            throws ReactorException {
        return ContextHelper.getFromDir(Stream.of(FileSystems.getDefault().getPath(dir)), accept);
    }

    private void createSwaggerFile(StringWriter writer, String swaggerFileName) throws IOException {
        // remove any existing swagger2.yml to avoid stale file from previous runs
        try {
            Path out = Paths.get(swaggerFileName);
            Files.deleteIfExists(out);
        } catch (Exception e) {
            // ignore any error while deleting
        }

        String yaml = writer.toString();

        try (PrintWriter out = new PrintWriter(swaggerFileName)) {
            out.print(yaml);
        }
    }
}
