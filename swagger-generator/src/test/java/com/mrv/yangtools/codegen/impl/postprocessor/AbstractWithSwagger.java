package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.junit.Before;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mrv.yangtools.codegen.impl.postprocessor.AbstractWithSwagger.Type.*;
import static java.util.Collections.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class AbstractWithSwagger {
    private static final String DEF_PREFIX = "#/definitions/";

    protected Swagger swagger;
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
        swagger.path("/c/propD", p("d", R));

    }

    protected Model m() {
        return m(emptySet(), emptyMap());
    }

    protected Model m(Set<String> parents, Map<String, String> properites) {
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



    protected Path p(String model) {
        return p(model, R);
    }


    protected Path p(String model, Type type) {
        Path path = new Path();
        if(RW == type || R == type)
            path.setGet(get(model));
        if(RW == type || W == type)
            path.setPost(post(model));
        return path;
    }

    protected Operation post(String model) {
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

    protected Operation get(String model) {
        final Operation get = new io.swagger.models.Operation();
        get.description("returns ");
        get.response(200, new Response()
                .schema(new RefProperty(DEF_PREFIX + model)
                        .description("desc")));
        return get;
    }

    static <K, V> Map.Entry<K, V> e(K key, V value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    static <K, U> Collector<Map.Entry<K, U>, ?, Map<K, U>> toMap() {
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
