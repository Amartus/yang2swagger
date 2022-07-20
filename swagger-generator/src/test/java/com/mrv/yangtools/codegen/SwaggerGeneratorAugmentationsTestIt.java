package com.mrv.yangtools.codegen;


import com.mrv.yangtools.codegen.impl.ModelUtils;
import io.swagger.models.*;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.RefProperty;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGeneratorAugmentationsTestIt extends AbstractItTest {
    @org.junit.Test
    public void testGenerateAugmentedGroupingsModuleOptimizing() {
        //when
        swaggerFor(p -> p.getFileName().toString().endsWith("groupings.yang"));

        //then
        assertEquals(3, swagger.getPaths().entrySet().stream().filter(e -> e.getKey().contains("g2-c-c1")).count());
        assertEquals(3, swagger.getDefinitions().keySet().stream().filter(e -> e.contains("augmenting")).count());
        assertEquals(11, swagger.getDefinitions().keySet().size());
        assertThat(swagger.getDefinitions().keySet(), hasItems("with.groupings.groupingroot.G1", "with.groupings.G2", "with.groupings.g2.g2c.G3"));
        Model model = swagger.getDefinitions().get("with.groupings.GroupingRoot");
        RefProperty groupingChild2 = (RefProperty) model.getProperties().get("grouping-child2");
        assertEquals("with.groupings.groupingroot.GroupingChild2", groupingChild2.getSimpleRef());
    }

    @org.junit.Test
    public void testGenerateAugmentation() {
        swaggerFor(p -> p.getFileName().toString().startsWith("simple"));

        Set<String> defNames = swagger.getDefinitions().keySet();

        assertEquals(new HashSet<>(Arrays.asList(
                "simplest.simpleroot.Children1", "simplest.SimpleRoot", "simplest.simpleroot.children1.Children2",
                "simpleaugmentation.simpleroot.AddedA", "simpleaugmentation.simpleroot.addeda.Children1",
                "simpleaugmentation.Children1Augmentation1", "simpleaugmentation.SimpleRootAugmentation1"
        )), defNames);

        checkLeafrefAreFollowed("simplest.simpleroot.children1.Children2", "parent-id", "integer");
        checkLeafrefAreFollowed("simpleaugmentation.simpleroot.AddedA", "a1", "string");
        assertThat(swagger.getPaths().keySet(), hasItem("/data/simple-root/added-a/children1"));
    }

    @org.junit.Test
    public void testAugGroupEx() {
        swaggerFor(p -> p.getParent().getFileName().toString().equals("aug-group-ex"));

        Model base = swagger.getDefinitions().get("base.Base");
        RefProperty c1 = (RefProperty) base.getProperties().get("c1");
        RefProperty c2 = (RefProperty) base.getProperties().get("c2");


        assertEquals("base.Coll",c1.getSimpleRef());
        assertEquals("base.base.C2",c2.getSimpleRef());
    }


    @org.junit.Test
    public void testBug15() {
        swaggerFor(p -> p.getParent().getFileName().toString().equals("bug_15"));

        Map<String, ComposedModel> attributeModels = swagger.getDefinitions().entrySet().stream()
                .filter(e -> e.getKey().endsWith(".Attributes"))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (ComposedModel) e.getValue()));

        assertEquals(2, attributeModels.size());
        ComposedModel regular = attributeModels.get("ext1.Attributes");
        ComposedModel augmented = attributeModels.get("ext1.job.Attributes");
        assertEquals(4, augmented.getAllOf().size());
        assertEquals(2, regular.getAllOf().size());
    }

    @org.junit.Test
    public void testBug17() {
        swaggerFor(p -> p.getParent().getFileName().toString().equals("bug_17"));

        Map<String, Model> augmented = swagger.getDefinitions().entrySet().stream()
                .filter(e -> ModelUtils.isAugmentation(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(3, swagger.getDefinitions().size());
        assertEquals(1, augmented.size());
    }

    @org.junit.Test
    public void testInheritenceWithAugmentation() {
        swaggerFor(p -> p.getParent().getFileName().toString().equals("inheritence-with-augmentation"));


        ComposedModel base = (ComposedModel) swagger.getDefinitions().get("base.Base");
        assertEquals(1, base.getInterfaces().size());
        assertEquals("base.Ident", base.getInterfaces().get(0).getSimpleRef());
        RefProperty managersRef = (RefProperty) ((ArrayProperty) base.getChild().getProperties().get("managers")).getItems();
        RefProperty usersRef = (RefProperty) ((ArrayProperty) base.getChild().getProperties().get("users")).getItems();


        ComposedModel managers = (ComposedModel) swagger.getDefinitions().get(managersRef.getSimpleRef());
        ComposedModel users = (ComposedModel) swagger.getDefinitions().get(usersRef.getSimpleRef());
        assertEquals(2, managers.getAllOf().size());
        assertEquals(2, managers.getInterfaces().size());
        //users are augmented
        assertEquals(3, users.getAllOf().size());
        assertEquals(2, users.getInterfaces().size());
    }

    @org.junit.Test
    public void testGenerateRPCModule() {

        final Consumer<Path> singlePostOperation = p -> {
            assertEquals(1, p.getOperations().size());
            assertNotNull(p.getPost());
        };
        //when

        swaggerFor(p -> p.getFileName().toString().startsWith("rpc-"));

        //then
        Map<String, Path> paths = swagger.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("/operations"))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

        assertEquals(4, paths.keySet().size());
        paths.values().forEach(singlePostOperation);

        Map<String, Model> rockTheHouseDefinitions = swagger.getDefinitions().entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().startsWith("rpc.basic.rockthe"))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        assertEquals(6, rockTheHouseDefinitions.size());
        Model inputAug = getAugmentation(rockTheHouseDefinitions.get("rpc.basic.rockthehouse.input.xyz.CRes"), "rpc-augmentations");
        Model outputAug = getAugmentation(rockTheHouseDefinitions.get("rpc.basic.rockthehouse.output.Response"), "rpc-augmentations");

        final String type = "rpc.augmentations.addition.AContainer";
        final String prop = "a-container";
        Function<Model, String> refType = m -> m instanceof ModelImpl ? ((RefProperty)m.getProperties().get(prop)).getSimpleRef(): null;

        assertNotNull(inputAug);
        assertNotNull(outputAug);
        assertEquals(type, refType.apply(inputAug));
        assertEquals(type, refType.apply(outputAug));
    }

    private Model getAugmentation(Model model, String prefix) {
        if(model == null || !(model instanceof ComposedModel)) return null;
        RefModel augRef = ((ComposedModel) model).getAllOf().stream()
                .filter(m -> m instanceof RefModel)
                .filter(m -> {
                    String ref = ((RefModel) m).getSimpleRef();
                    Model aug = swagger.getDefinitions().get(ref);
                    Map<String, String> ext = (Map<String, String>) aug.getVendorExtensions().getOrDefault("x-augmentation", Collections.emptyMap());
                    return prefix.equals(ext.get("prefix"));
                })
                .map(m -> ((RefModel)m))
                .findFirst().orElse(null);
        return augRef == null ? null : swagger.getDefinitions().get(augRef.getSimpleRef());
    }

}
