package com.mrv.yangtools.codegen.impl.postprocessor;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.test.utils.SwaggerReader;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.RefParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollapseTypesTest {
    @Test
    public void testReduceSameStructures() {
        Swagger swagger = swaggerForClasspath("/replace-definitions-minimal.yaml");

        long initial = countDefinitions(swagger);

        new CollapseTypes().accept(swagger);

        long target = countDefinitions(swagger);

        Assert.assertEquals(initial - 1, target);
        MatcherAssert.assertThat(danglingRefs(swagger), CoreMatchers.equalTo(new HashSet<String>()));
    }

    private Stream<String> references(Property model) {
        if(model instanceof ArrayProperty) {
            return references(((ArrayProperty) model).getItems());
        }
        if(model instanceof RefProperty) {
            return Stream.of(((RefProperty) model).get$ref());
        }
        return Stream.empty();
    }

    private Stream<String> references(Model model) {
        if(model == null) {
            return Stream.empty();
        }
        if(model.getReference() != null) {
            return Stream.of(model.getReference());
        }
        if(model instanceof ModelImpl) {
            return model.getProperties().values()
                    .stream().flatMap(this::references);
        }
        if(model instanceof ComposedModel) {
            ComposedModel cm = (ComposedModel) model;
            return cm.getAllOf().stream().flatMap(this::references);
        }
        return Stream.empty();
    }

    private Stream<String> references(Swagger swagger) {
        return Stream.concat(
            swagger.getDefinitions().values().stream()
                .flatMap(this::references),
            swagger.getPaths().values().stream()
                        .flatMap(this::references)
        );
    }

    private Stream<String> references(Path path) {
        return path.getOperations().stream()
                .flatMap(this::references);
    }

    private Stream<String> references(Operation operation) {
        return Stream.concat(
                operation.getParameters().stream().flatMap(this::references),
                operation.getResponses().values().stream().flatMap(this::references)
        );
    }

    private Stream<String> references(Parameter parameter) {
        if(parameter instanceof RefParameter) {
            return Stream.of(((RefParameter) parameter).get$ref());
        }
        if(parameter instanceof BodyParameter) {
            return references(((BodyParameter) parameter).getSchema());
        }
        return Stream.empty();
    }
    private Stream<String> references(Response response) {
        return references(response.getResponseSchema());
    }
    private Set<String> danglingRefs(Swagger swagger) {
        Set<String> definitions =  swagger.getDefinitions().keySet();
        Set<String> allRef = references(swagger)
                .filter(r -> r.startsWith("#/definitions"))
                .map(r -> r.substring("#/definitions/".length()))
                .collect(Collectors.toSet());
        allRef.removeAll(definitions);
        return allRef;
    }

    protected long countDefinitions(Swagger swagger) {
        return countDefinitions(swagger, it -> true);
    }
    protected long countDefinitions(Swagger swagger, Predicate<Map.Entry<String, Model>> filter) {
        return swagger.getDefinitions().entrySet().stream()
                .filter(filter)
                .count();
    }

    protected Swagger swaggerForClasspath(String path) {
        try(InputStream input = CollapseTypesTest.class
                .getResourceAsStream(path)) {

            return new SwaggerReader().read(new InputStreamReader(input), SwaggerGenerator.Format.YAML);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

}
