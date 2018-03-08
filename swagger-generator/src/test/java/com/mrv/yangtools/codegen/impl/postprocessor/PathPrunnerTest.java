package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.hamcrest.core.Every;
import org.hamcrest.core.StringStartsWith;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mrv.yangtools.codegen.impl.postprocessor.PathPrunnerTest.Type.*;
import static java.util.Collections.*;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static com.mrv.yangtools.codegen.impl.postprocessor.PathPrunner.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class PathPrunnerTest {

    private static final String DEF_PREFIX = "#/definitions/";

    private Swagger swagger;
    enum Type {R, W, RW};

    @Before
    public void setupSwagger() {
        swagger = new Swagger();
        swagger.setPaths(new HashMap<>());
        swagger.addDefinition("Parent1", m());
        swagger.addDefinition("Parent2", m());

        Model a = m(singleton("Parent1"), emptyMap());
        Model b = m(Stream.of("Parent1", "Parent2").collect(Collectors.toSet()), unmodifiableMap(Stream.of(
                e("propE", "e"),
                e("propNW", "Parent2")
        ).collect(toMap())));

        Model c = m(singleton("Parent2"), unmodifiableMap(Stream.of(
                e("propD", "d")
        ).collect(toMap())));

        Model d = m(singleton("Parent1"), emptyMap());

        Model e = m(singleton("Parent2"), unmodifiableMap(Stream.of(
                e("propF", "f")
        ).collect(toMap())));

        Model f = m(singleton("a"), emptyMap());

        swagger.addDefinition("a", a);
        swagger.addDefinition("b", b);
        swagger.addDefinition("c", c);
        swagger.addDefinition("d", d);
        swagger.addDefinition("e", e);
        swagger.addDefinition("f", f);

        swagger.path("/a", p("a"));
        swagger.path("/b", p("b", W));
        swagger.path("/b/propE", p("e", R));
        swagger.path("/b/propP2", p("Parent2", R));
        swagger.path("/b/propE/propF", p("f", W));
        swagger.path("/c", p("c", RW));
        swagger.path("/c/propF", p("d", R));
    }

    @Test
    public void noChange() {
        int orgPathsCnt = swagger.getPaths().size();
        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner().accept(swagger);
        assertEquals(orgPathsCnt, swagger.getPaths().size());
        assertEquals(orgDefCnt, swagger.getDefinitions().size());

    }

    @Test
    public void prunePathB() {
        int orgPathsCnt = swagger.getPaths().size();
        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner()
                .prunePath("/b")
                .accept(swagger);
        assertEquals(orgPathsCnt - 4, swagger.getPaths().size());
        assertEquals(orgDefCnt, swagger.getDefinitions().size());
        Assert.assertThat(swagger.getPaths().keySet(), Every.everyItem(not(StringStartsWith.startsWith("/b"))));
    }

    @Test
    public void prunePathBA() {
        int orgPathsCnt = swagger.getPaths().size();
        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner()
                .prunePath("/b/propE")
                .prunePath("/a")
                .accept(swagger);
        assertEquals(orgPathsCnt - 3, swagger.getPaths().size());
        assertEquals(orgDefCnt, swagger.getDefinitions().size());
        Assert.assertThat(swagger.getPaths().keySet(), Every.everyItem(not(StringStartsWith.startsWith("/b/propE"))));
        Assert.assertThat(swagger.getPaths().keySet(), Every.everyItem(not(StringStartsWith.startsWith("/a"))));
        Assert.assertThat(swagger.getPaths().keySet(), hasItem(StringStartsWith.startsWith("/b")));
    }

    @Test
    public void pruneParent2() {
        int orgPathsCnt = swagger.getPaths().size();
        int orgDefCnt = swagger.getDefinitions().size();
        new PathPrunner()
                .withType("Parent1")
                .accept(swagger);
        assertEquals(2, swagger.getPaths().size());
        Assert.assertThat(swagger.getPaths().keySet(), hasItems(
                is("/a"),
                is("/b")
        ));
        assertEquals(orgDefCnt, swagger.getDefinitions().size());
    }

    private Model m() {
        return m(emptySet(), emptyMap());
    }

    private Model m(Set<String> parents, Map<String, String> properites) {
        Objects.requireNonNull(parents);
        Objects.requireNonNull(properites);

        if(properites.isEmpty() && parents.isEmpty()) return new ModelImpl();

        ComposedModel model = new ComposedModel();
        model.setAllOf(
                parents.stream()
                        .map(p -> new RefModel(DEF_PREFIX + p))
                        .collect(Collectors.toList())
        );

        if(!properites.isEmpty()) {
            final ModelImpl m = new ModelImpl();
            Map<String, RefProperty> refs = properites.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new RefProperty(DEF_PREFIX + e.getValue())));
            refs.forEach(m::addProperty);
            model.getAllOf().add(m);
        }

        return model;
    }



    private Path p(String model) {
        return p(model, R);
    }


    private Path p(String model, Type type) {
        Path path = new Path();
        if(RW == type || R == type)
            path.setGet(get(model));
        if(RW == type || W == type)
            path.setPost(post(model));
        return path;
    }

    private Operation post(String model) {
        final Operation post = new io.swagger.models.Operation();
        final RefModel definition = new RefModel(model);
        post.description("creates ");
        post.parameter(new BodyParameter()
                .name("body-param")
                .schema(definition)
                .description("desc"));

        post.response(201, new Response().description("Object created"));
        post.response(409, new Response().description("Object already exists"));
        return post;
    }

    private Operation get(String model) {
        final Operation get = new io.swagger.models.Operation();
        get.description("returns ");
        get.response(200, new Response()
                .schema(new RefProperty(DEF_PREFIX + model)
                        .description("desc")));
        return get;
    }

    static <K, U> Collector<Map.Entry<K, U>, ?, Map<K, U>> toMap() {
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}