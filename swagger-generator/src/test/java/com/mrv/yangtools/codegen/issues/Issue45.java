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
    public void testIssue45() throws IOException, ReactorException {
        runSwaggerGeneratorWithMountMappings();

        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.writeValue(writer, swagger);

        ComposedModel specificConfig = (ComposedModel) swagger.getDefinitions().get("list.manager.listentry.SpecificConfig");
        Assert.assertEquals("entry.type._1.Content", ((RefModel) specificConfig.getAllOf().get(0)).getSimpleRef());
        Assert.assertEquals("entry.type._2.Content", ((RefModel) specificConfig.getAllOf().get(1)).getSimpleRef());

        String yaml = writer.toString();

        try (PrintWriter out = new PrintWriter("swagger.yml")) {
            out.print(yaml);
        }
    }

    private void runSwaggerGeneratorWithMountMappings() throws ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");
        final EffectiveModelContext context = buildEffectiveModelContext(
                Paths.get("src","test","resources","bug_45").toString(),
                p -> matcher.matches(p.getFileName()));

        Collection<? extends Module> modulesToGenerate = context.getModules().stream()
                .filter(module -> module.getName().equals("list-manager")
                        || module.getName().equals("entry-type-1"))
                .collect(Collectors.toList());

        SwaggerGenerator generator = new SwaggerGenerator(context, modulesToGenerate).defaultConfig();
        Map<String, List<MountPointTarget>> mappingMap = new HashMap<>();
        mappingMap.put("list-entry-data", Arrays.asList(
                MountPointTarget.parse("entry-type-1:content"),
                MountPointTarget.parse("entry-type-2:content")
        ));
        generator.yangmntMappings(new MountPointMappings(mappingMap));
        swagger = generator.generate();
    }

    private EffectiveModelContext buildEffectiveModelContext(String dir, Predicate<Path> accept)
            throws ReactorException {
        return ContextHelper.getFromDir(Stream.of(FileSystems.getDefault().getPath(dir)), accept);
    }
}
