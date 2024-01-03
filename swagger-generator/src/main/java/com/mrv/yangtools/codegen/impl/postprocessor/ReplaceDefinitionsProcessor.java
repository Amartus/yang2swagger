/*
 * Copyright (c) 2024 Amartus. All rights reserved.
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
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Processor that allows replacing one definition with another one in any swagger.
 * This implementation is simple and limited only to the type definitions that are aggregators of references.
 * @author bartosz.michalik@amartus.com
 */
public abstract class ReplaceDefinitionsProcessor implements Consumer<Swagger> {
    private final Logger log = LoggerFactory.getLogger(ReplaceDefinitionsProcessor.class);
    @Override
    public void accept(Swagger target) {
        Map<String, String> replacements = optimize(prepareForReplacement(target));

        log.debug("{} replacement found for definitions", replacements.size());
        log.trace("replacing paths");
        target.getPaths().values().stream().flatMap(p -> p.getOperations().stream())
                .forEach(o -> fixOperation(o, replacements));

        target.getDefinitions().forEach((key, value) -> fixModel(key, value, replacements));
        replacements.keySet().forEach(r -> {
            log.debug("removing {} model from swagger definitions", r);
            target.getDefinitions().remove(r);
        });
    }



    private Map<String, String> optimize(Map<String, String> replacements) {
        Function<String, String> last = s -> {
            String replacement = replacements.get(s);
            while (replacement != null) {
                s = replacement;
                replacement = replacements.get(s);
            }
            return s;

        };

        return replacements.entrySet().stream().map(e -> Map.entry(e.getKey(), last.apply(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    protected abstract Map<String, String> prepareForReplacement(Swagger swagger);

    private Optional<Model> fixModel(String name, Model m, Map<String, String> replacements) {

        if(m != null && replacements.containsKey(name)) {
            return Optional.empty();
        }

        if(m instanceof RefModel) {
            return Optional.ofNullable(fixRefModel((RefModel) m, replacements));
        }

        if(m instanceof ComposedModel) {
            ComposedModel cm = (ComposedModel) m;
            fixComposedModel(cm, replacements);
            Stream<Entry<String, Property>> propAll = cm.getAllOf().stream()
                .flatMap(this::properties);
            fixProperties(propAll, replacements);
        }
        fixProperties(properties(m), replacements);
        return Optional.empty();
    }

    private Stream<Entry<String, Property>> properties(Model m) {
        if(m == null) return Stream.empty();
        return Optional.ofNullable(m.getProperties()).stream()
            .flatMap(it -> it.entrySet().stream());
    }

    private void fixProperties(Stream<Entry<String, Property>> properties, Map<String, String> replacements) {
        properties.forEach(e -> {
            var key = e.getKey();
            var value = e.getValue();
            if (value instanceof RefProperty) {
                if (fixProperty((RefProperty) value, replacements)) {
                    log.debug("fixing property {}", key);
                }
            } else if (value instanceof ArrayProperty) {
                Property items = ((ArrayProperty) value).getItems();
                if (items instanceof RefProperty) {
                    if (fixProperty((RefProperty) items, replacements)) {
                        log.debug("fixing property {}", key);
                    }
                }
            }
        });
    }


    private boolean fixProperty(RefProperty p, Map<String, String> replacements) {
        if(replacements.containsKey(p.getSimpleRef())) {
            p.set$ref("#/definitions/" + replacements.get(p.getSimpleRef()));
            return true;
        }
        return false;
    }

    private void fixComposedModel(ComposedModel m, Map<String, String> replacements) {
        Set<RefModel> toReplace = m.getAllOf().stream().filter(c -> c instanceof RefModel).map(cm -> (RefModel) cm)
                .filter(rm -> replacements.containsKey(rm.getSimpleRef())).collect(Collectors.toSet());
        toReplace.forEach(r -> {
            int idx = m.getAllOf().indexOf(r);
            RefModel newRef = new RefModel(replacements.get(r.getSimpleRef()));
            m.getAllOf().set(idx, newRef);
            if(m.getInterfaces().remove(r)) {
                m.getInterfaces().add(newRef);
            }
        });
    }

    private Model fixRefModel(RefModel model, Map<String, String> replacements) {
        if(replacements.containsKey(model.getSimpleRef())) {
            model.set$ref("#/definitions/" + replacements.get(model.getSimpleRef()));
            return model;
        }
        return null;
    }

    private void fixOperation(Operation operation, Map<String, String> replacements) {
        operation.getResponses().values()
                .forEach(r -> fixResponse(r, replacements));
        operation.getParameters().forEach(p -> fixParameter(p, replacements));
        Optional<Map.Entry<String, String>> rep = replacements.entrySet().stream()
                .filter(r -> operation.getDescription() != null && operation.getDescription().contains(r.getKey()))
                .findFirst();
        if(rep.isPresent()) {
            log.debug("fixing description for '{}'", rep.get().getKey());
            Map.Entry<String, String> entry = rep.get();
            operation.setDescription(operation.getDescription().replace(entry.getKey(), entry.getValue()));
        }

    }

    private void fixParameter(Parameter p, Map<String, String> replacements) {
        if(!(p instanceof BodyParameter)) return;
        BodyParameter bp = (BodyParameter) p;

        fixModel(null, bp.getSchema(), replacements)
            .ifPresent(m -> {
                bp.setSchema(m);
                var nType = toTypeName(m.getReference());
                var rep = replacements.entrySet().stream()
                    .filter(e -> e.getValue().equals(nType))
                    .findFirst();
                rep.ifPresent(e -> {
                    var nDesc = bp.getDescription().replace(e.getKey(), e.getValue());
                    bp.setDescription(nDesc);
                });

            });
    }

    private void fixResponse(Response r, Map<String, String> replacements) {
        Model model = r.getResponseSchema();
        if(model != null) {
            fixModel(null, model, replacements)
                .ifPresent(m -> {
                    r.setResponseSchema(m);
                    r.setDescription(toTypeName(m.getReference()));
                });
        }
    }

    private static String toTypeName(String ref) {
        if(ref == null) return null;
        var seg = ref.split("/");
        return seg[seg.length - 1];
    }
}
