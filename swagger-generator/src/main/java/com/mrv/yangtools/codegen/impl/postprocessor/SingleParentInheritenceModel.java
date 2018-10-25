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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mrv.yangtools.codegen.impl.ModelUtils.isAugmentation;
import static com.mrv.yangtools.codegen.impl.postprocessor.SwaggerRefHelper.getReferences;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SingleParentInheritenceModel implements Consumer<Swagger> {
    private static final Logger log = LoggerFactory.getLogger(SingleParentInheritenceModel.class);
    @Override
    public void accept(Swagger swagger) {

        Worker worker = new Worker(buildHierarchy(swagger), swagger);

        swagger.getDefinitions().entrySet().stream()
                .filter(e -> worker.getReferencing(e.getKey()).size() > 1)
                .map(e -> {

                    worker.compute(e.getKey());
                    String parent = worker.getParent();
                    Set<String> toUnpack = worker.getToUnpack();


                    ComposedModel model = new ComposedModel();

                    model.setParent(new RefModel(parent));

                    ModelImpl impl = new ModelImpl();

                    toUnpack.forEach(u -> {
                        log.debug("Unpacking {}", u);
                        Model m = swagger.getDefinitions().get(u);
                        if(m instanceof ModelImpl) {
                            copyAttributes(impl, (ModelImpl) m);
                        } else if(m instanceof ComposedModel) {
                            Optional<ModelImpl> tU = ((ComposedModel) m).getAllOf().stream().filter(x -> x instanceof ModelImpl).map(x -> (ModelImpl) x)
                                    .findFirst();
                            tU.ifPresent(model1 -> copyAttributes(impl, model1));
                        }
                    });

                    model.setChild(impl);


                    return new AbstractMap.SimpleEntry<String, Model>(e.getKey(), model);

        }).forEach(e -> swagger.addDefinition(e.getKey(), e.getValue()));


    }

    private class Worker {
        private final Map<String, TypeNode> hierarchy;
        private final Swagger swagger;
        private Set<String> toUnpack;
        private String parent;

        private Worker(Map<String, TypeNode> hierarchy, Swagger swagger) {
            this.hierarchy = hierarchy;
            this.swagger = swagger;
        }

        private Set<TypeNode> getReferencing(String type) {
            return hierarchy.get(type).getReferencing();
        }

        private void compute(String type) {

            TypeNode node = hierarchy.get(type);

            Set<TypeNode> typesToUnpack = getAllInHierarchy(node).collect(Collectors.toSet());
            TypeNode parentType = findParent(typesToUnpack);
            typesToUnpack.remove(parentType);

            toUnpack = typesToUnpack.stream().map(t -> t.type).collect(Collectors.toSet());
            parent = parentType.type;


        }

        private TypeNode findParent(Set<TypeNode> typesToUnpack) {
            return typesToUnpack.stream().reduce((a,b) -> a.getReferencedBy().size() > b.getReferencedBy().size() ? a : b).get();
        }

        Stream<TypeNode> getAllInHierarchy(TypeNode node) {
            if(node.getReferencing().size() > 0) return Stream.concat(Stream.of(node), sorted(node.getReferencing()).flatMap(this::getAllInHierarchy));
            return Stream.of(node);
        }

        private Stream<TypeNode> sorted(Set<TypeNode> referencing) {
            TreeSet<TypeNode> sorted = new TreeSet<>((a, b) -> {
                Model modelA = swagger.getDefinitions().get(a.type);
                Model modelB = swagger.getDefinitions().get(b.type);
                boolean aAugmentation = isAugmentation(modelA);
                boolean bAugmentation = isAugmentation(modelB);

                if(aAugmentation) {
                    if(bAugmentation) return a.type.compareTo(b.type);
                    return 1;
                }
                if(bAugmentation) return -1;
                return a.type.compareTo(b.type);
            });
            sorted.addAll(referencing);
            return sorted.stream();
        }

        private Set<String> getToUnpack() {
            return toUnpack;
        }

        private String getParent() {
            return parent;
        }
    }

    private Map<String, TypeNode> buildHierarchy(Swagger swagger) {
        TypesUsageTreeBuilder typesUsageTreeBuilder = new TypesUsageTreeBuilder();

        swagger.getDefinitions().forEach((type, value) -> {
            Stream<String> references = getReferences(type, value);
            typesUsageTreeBuilder.referencing(type, references);
        });

        return typesUsageTreeBuilder.build();
    }


    private void copyAttributes(ModelImpl target, ModelImpl source) {
        //TODO may require property copying and moving x- extensions down to properties
        if(source.getProperties() != null)
            source.getProperties().forEach(target::addProperty);
    }
}
