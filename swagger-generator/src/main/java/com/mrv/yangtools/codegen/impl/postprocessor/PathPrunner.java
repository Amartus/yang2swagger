/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.RefProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Leave only data paths to given type hierarchies
 * or prune using path definitions.
 * If nothing is specified swagger model remains intact.
 * @author bartosz.michalik@amartus.com
 */
public class PathPrunner implements Consumer<Swagger> {

    private final Logger log = LoggerFactory.getLogger(PathPrunner.class);

    private final List<String> exclude;
    private Set<String> types;
    private Set<String> paths;

    /**
     * @param excludePrefixes excluded from analysis
     */
    public PathPrunner(String... excludePrefixes) {
        this.exclude = Arrays.asList(excludePrefixes);
        this.types = new HashSet<>();
        this.paths = new HashSet<>();
    }

    Predicate<String> startsWith(Collection<String> prefixes) {
        return candidate -> prefixes.stream().anyMatch(candidate::startsWith);
    }

    /**
     * Keep path refereing to type
     * @param type
     * @return
     */
    public PathPrunner withType(String type) {
        types.add(type);
        return this;
    }

    /**
     * Add path to be pruned. All path starting with parameter are pruned
     * @param startingWith
     * @return
     */
    public PathPrunner prunePath(String startingWith) {
        paths.add(startingWith);
        return this;
    }

    @Override
    public void accept(Swagger swagger) {
        if(swagger.getPaths() == null) return;
        prunePaths(swagger);

        if(swagger.getDefinitions() == null || types.isEmpty()) return;
        pruneByType(swagger);
    }

    void pruneByType(Swagger swagger) {
        Predicate<String> excludedPaths = startsWith(exclude);
        final Map<String, Set<String>> hierarchy = buildTypeHierarchy(swagger);

        final Set<String> toRemove = swagger.getPaths().entrySet().stream()
                .filter(e -> !excludedPaths.test(e.getKey()))
                .filter(e -> removeByType(e.getKey(), e.getValue(), hierarchy))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        swagger.setPaths(swagger.getPaths().entrySet().stream().filter(p -> {
            boolean remove = toRemove.stream().anyMatch(tR -> p.getKey().startsWith(tR));
            if(remove) log.debug("Removing path based on type {}", p.getKey());
            return !remove;
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static Stream<String> inheritenceStructure(String type, Map<String, Set<String>> hierarchy) {
        return Stream.concat(Stream.of(type), hierarchy.get(type).stream().flatMap(p -> inheritenceStructure(p, hierarchy)));
    }

    /**
     * Remove all path referring types that are not conforming to the configured types {@link PathPrunner#withType(String)}
     * @param path path to examine
     * @param hierarchy type hierarchy
     * @return true if path should be pruned
     */
    private boolean removeByType(String pathName, Path path, final Map<String, Set<String>> hierarchy) {

        Operation get = path.getGet();
        if(get != null) {
           return toRemoveByResponse(pathName, get, hierarchy);
        }
        Operation post = path.getPost();
        if(post != null) {
           return toRemoveByBody(pathName, "POST", post, hierarchy);
        }

        Operation put = path.getPut();
        if(put != null) {
            return toRemoveByBody(pathName, "PUT", put, hierarchy);
        }

        return false;
    }

    private boolean toRemoveByBody(String pathName, String operationName, Operation o, final Map<String, Set<String>> hierarchy) {
        String modelId = getFromBody(o);
        if(modelId == null) {
            log.warn("not recognized {} path {}. Skipping.", operationName, pathName);
            return false;
        }
        return ! inheritenceStructure(modelId, hierarchy).anyMatch(m -> types.contains(m));
    }

    private boolean toRemoveByResponse(String pathName, Operation o, final Map<String, Set<String>> hierarchy) {
        String modelId = getFromResponse(o, "200");
        if(modelId == null) {
            log.warn("not recognized GET path {}. Skipping.", pathName);
            return false;
        }

        return ! inheritenceStructure(modelId, hierarchy).anyMatch(m -> types.contains(m));
    }



    private String getFromResponse(Operation oper, String responseCode) {
        Response response = oper.getResponses().get(responseCode);
        if(response == null || ! (response.getSchema() instanceof RefProperty)) {
            return null;
        }
        RefProperty schema = (RefProperty) response.getSchema();
        return schema.getSimpleRef();
    }

    private String getFromBody(Operation oper) {
        Optional<Parameter> bodyParam =
                oper.getParameters().stream().filter(p -> p instanceof BodyParameter).findFirst();
        if(bodyParam.isPresent()) {
            Model schema = ((BodyParameter) bodyParam.get()).getSchema();
            if(schema instanceof RefModel) {
                return ((RefModel) schema).getSimpleRef();
            }
        }

        return null;
    }

    private void prunePaths(Swagger swagger) {
        Predicate<String> match = startsWith(paths);
        Predicate<String> excluded = startsWith(exclude);

        if(paths.isEmpty()) return;

        Map<String, Path> paths = swagger.getPaths();
        Set<String> toRemove = paths.entrySet().stream()
                .filter(e -> !excluded.test(e.getKey()) && match.test(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        toRemove.forEach(p -> {
            log.debug("Removing path based on type {}", p);
            paths.remove(p);
        });
        swagger.setPaths(paths);
    }

    private Map<String, Set<String>> buildTypeHierarchy(Swagger swagger) {

        return swagger.getDefinitions().entrySet().stream().map(e -> {
            Model m = e.getValue();
            if (m instanceof ComposedModel) {
                Set<String> parents = ((ComposedModel) m).getAllOf().stream()
                        .filter(c -> c instanceof RefModel)
                        .map(c -> ((RefModel) c).getSimpleRef()).collect(Collectors.toSet());
                return e(e.getKey(), parents);
            } else return e(e.getKey(), Collections.emptySet());

        }).collect(Collectors.toMap(Map.Entry::getKey, e -> (Set<String>) e.getValue()));

    }

    static <K, V> Map.Entry<K, V> e(K key, V value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }
}
