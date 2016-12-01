/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl;

import io.swagger.models.*;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The builder strategy is to reuse grouping wherever possible. Therefore in generated Swagger models, groupings are transformed to models
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class OptimizingDataObjectBuilder extends AbstractDataObjectBuilder {
    private static final Logger log = LoggerFactory.getLogger(OptimizingDataObjectBuilder.class);

    private HashMap<SchemaPath, GroupingDefinition> groupings;

    private Map<SchemaNode, Model> existingModels;
    private final GroupingHierarchyHandler groupingHierarchyHandler;
    private Map<Object, Set<UsesNode>> usesCache;

    public OptimizingDataObjectBuilder(SchemaContext ctx, Swagger swagger, TypeConverter converter) {
        super(ctx, swagger, converter);
        groupings = new HashMap<>();
        existingModels = new HashMap<>();
        usesCache = new HashMap<>();
        groupingHierarchyHandler = new GroupingHierarchyHandler(ctx);

        Set<Module> allModules = ctx.getModules();
        HashSet<String> names = new HashSet<>();
        allModules.forEach(m -> processGroupings(m, names));
    }


    @Override
    public <T extends SchemaNode & DataNodeContainer> String getName(T node) {
        if(isGrouping(node)) {
            return names.get(grouping(node));
        }
        return names.get(node);
    }

    private static Predicate<SchemaNode> isAugmented = (n -> (n instanceof AugmentationTarget) &&
            !((AugmentationTarget) n).getAvailableAugmentations().isEmpty());

    private <T extends SchemaNode & DataNodeContainer> GroupingDefinition grouping(T node) {
        Set<UsesNode> uses = uses(node);
        assert uses.size() == 1;
        //noinspection SuspiciousMethodCalls

        return groupings.get(uses.iterator().next().getGroupingPath());
    }

    /**
     * Is node that has no attributes only single grouping.
     * @param node to check
     * @return <code>true</code> if node is using single grouping and has no attributes
     */
    @SuppressWarnings("unchecked")
    private  <T extends SchemaNode & DataNodeContainer> boolean isGrouping(SchemaNode node) {
        if(node instanceof AugmentationTarget) {
            if(isAugmented.test(node)) return false;
        }
        if(node instanceof DataNodeContainer) {
            Set<UsesNode> uses = uses((T) node);
            if(uses.size() == 1) {
                return ((DataNodeContainer) node).getChildNodes().stream()
                        .filter(n -> !n.isAddedByUses()).count() == 0;
            }
        }
        return false;
    }

    @Override
    protected String generateName(SchemaNode node, String proposedName, Set<String> cache) {
        if(node instanceof DerivableSchemaNode) {

            com.google.common.base.Optional<? extends SchemaNode> original = ((DerivableSchemaNode) node).getOriginal();
            if(original.isPresent()){
                log.debug("reusing original definition to get name for {}", node.getQName());
                return super.generateName(original.get(), proposedName, cache);
            }
        }
        return super.generateName(node, proposedName, cache);
    }

    @Override
    protected void processNode(DataNodeContainer container, Set<String> cache) {
        super.processNode(container, cache);
//        processGroupings(container, cache);
    }

    protected void processGroupings(DataNodeContainer container, Set<String> cache) {
        DataNodeHelper.stream(container).filter(n -> n instanceof GroupingDefinition)
                .forEach(n -> {
                    String gName = groupingHierarchyHandler.getGroupingName((GroupingDefinition) n);
                    if(gName != null) {
                        gName = generateName(n, gName, cache);
                    } else {
                        gName = generateName(n, null, cache);
                        if(names.values().contains(gName)) {
                            //TODO better strategy to handle grouping names
                            gName = "G" + gName;
                        }
                    }
                    names.put(n, gName);
                    groupings.put(n.getPath(), (GroupingDefinition) n);
                });
    }

    @Override
    protected <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node) {
        final String definitionId = getDefinitionId(node);
        log.debug("reference to {}", definitionId);
        RefProperty prop = new RefProperty(definitionId);

        if(existingModel(node) == null) {
            log.debug("adding referenced model {} for node {} ", definitionId, node);
            addModel(node);
        }

        return prop;
    }




    @Override
    public <T extends SchemaNode & DataNodeContainer> Model build(T node) {
        Model model = existingModel(node);
        if(model != null) return model;

        return model(node);
    }

    @SuppressWarnings("unchecked")
    private <T extends SchemaNode & DataNodeContainer> Model existingModel(T node) {
        T toModel = isGrouping(node) ? (T) grouping(node) : node;
        return existingModels.get(toModel);
    }


    /**
     * Create model for a node with zero of single uses
     * @param node to process
     * @param <T> type
     * @return model or null in case more than one grouping is used
     */
    @SuppressWarnings("unchecked")
    private <T extends SchemaNode & DataNodeContainer> Model model(T node) {
        T tmp;
        T toModel = node;
        boolean simpleModel;
        do {
            tmp = toModel;
            simpleModel = isGrouping(toModel) || uses(toModel).isEmpty();
            toModel = isGrouping(toModel) ? (T) grouping(toModel) : toModel;
            if(log.isDebugEnabled() && tmp != toModel) {
                log.debug("substitute {} with {}", tmp.getQName(), toModel.getQName());
            }
        } while(tmp != toModel && simpleModel);

        Model model = simpleModel ? simple(toModel) : composed(toModel);

        existingModels.put(toModel, model);

        return model;
    }

    private <T extends SchemaNode & DataNodeContainer> Set<UsesNode> uses(T toModel) {
        if(usesCache.containsKey(toModel)) {
            return usesCache.get(toModel);
        }
        final Set<UsesNode> uses = new HashSet<>(toModel.getUses());
        Set<UsesNode> result = uses;

        if(result.size() > 1) {
            result = optimizeInheritance(uses);
        }
        usesCache.put(toModel, result);
        return result;
    }

    private Set<UsesNode> optimizeInheritance(Set<UsesNode> result) {
        return result.stream().filter(r -> {
            SchemaPath rName = r.getGroupingPath();

            return ! result.stream().filter(o -> ! o.equals(r))
                    .map(o -> groupingHierarchyHandler.isParent(rName, o.getGroupingPath().getLastComponent()))
                    .filter(hasParent -> hasParent)
                    .findFirst().orElse(false);
        }).collect(Collectors.toSet());
    }

    private <T extends SchemaNode & DataNodeContainer> Model composed(T node) {
        ComposedModel newModel = new ComposedModel();


        // because of for swagger model order matters we need to add attributes at the end
        List<RefModel> models = uses(node).stream().map(u -> {
            String groupingIdx = getDefinitionId(groupings.get(u.getGroupingPath()));
            log.debug("adding grouping {} to composed model", groupingIdx);
            RefModel refModel = new RefModel(groupingIdx);

            if (existingModel(node) == null) {
                log.debug("adding model {} for grouping", groupingIdx);
                addModel(groupings.get(u.getGroupingPath()));
            }
            return refModel;
        }).collect(Collectors.toList());

        models = optimizeInheritance(models);

        if(models.size() > 1) {
            log.warn("Multiple inheritance for {}", node.getQName());
        }
        newModel.setInterfaces(models);
        if(!models.isEmpty())
            newModel.child(models.get(0));

        final ModelImpl attributes = new ModelImpl();
        attributes.description(desc(node));
        attributes.setProperties(structure(node));
        attributes.setDiscriminator("objType");
        boolean noAttributes = attributes.getProperties() == null || attributes.getProperties().isEmpty();
        if(! noAttributes) {
            newModel.child(attributes);
        }

        if(models.size() == 1 && noAttributes) {
            log.warn("should not happen to have such object for node {}", node);
        }

        return newModel;
    }

    private List<RefModel> optimizeInheritance(List<RefModel> models) {
        if(models.size() < 2) return models;
        Map<RefModel, Set<String>> inheritance = models.stream()
                .map(m -> new RefModelTuple(m, inheritanceId(m)))
                .collect(Collectors.toMap(RefModelTuple::first, RefModelTuple::second, (v1, v2) -> {v1.addAll(v2); return v1;}));

        //duplicates
        HashSet<String> nameCache = new HashSet<>();
        List<RefModel> resultingModels = models.stream().filter(m -> {
            String sn = m.getSimpleRef();
            boolean result = !nameCache.contains(sn);
            if (!result && log.isDebugEnabled()) {
                log.debug("duplicated models with name {}", sn);
            }
            nameCache.add(sn);
            return result;
        })
                // inheritance structure
                .filter(model -> {
                    Set<String> mine = inheritance.get(model);

                    // we leave only these models for which there is none more specific
                    // so if exist at least one more specific we can remove model
                    boolean existsMoreSpecific = inheritance.entrySet().stream()
                            .filter(e -> !e.getKey().equals(model))
                            .map(e -> moreSpecific(e.getValue(), mine))
                            .filter(eMoreSpecific -> eMoreSpecific).findFirst().orElse(false);

                    if (existsMoreSpecific && log.isDebugEnabled()) {
                        log.debug("more specific models found than {}", model.getSimpleRef());
                    }
                    return !existsMoreSpecific;
                }).collect(Collectors.toList());

        if(resultingModels.size() != models.size()) {
            log.debug("optimization succeeded from {} to {}", models.size(), resultingModels.size());
        }
        return resultingModels;
    }

    /**
     * All of 'mine' strings are more specific than 'yours'.
     * In  other words for each of 'yours' exists at least one 'mine' which is more specific
     * @param mine mine ids
     * @param yours you
     * @return <code>true</code> if more specific
     */
    protected static boolean moreSpecific(Set<String> mine, Set<String> yours) {
        return yours.stream()
                .map(yString -> mine.stream()
                        .map(mString -> mString.contains(yString))
                        //exist in mine at least one string that contain yString [1]
                        .filter(contains -> contains).findFirst().orElse(false)
                )
                //does not exist any your string that is incompatible with (1)
                .filter(x -> !x).findFirst().orElse(true);
    }

    private Set<String> inheritanceId(RefModel m) {

        String id = m.getSimpleRef();

        Model model = swagger.getDefinitions().get(id);
        if(model instanceof ModelImpl) return Collections.singleton(id);

        if(model instanceof RefModel) {
            return inheritanceId((RefModel) model)
                    .stream().map(s -> id + s)
                    .collect(Collectors.toSet());
        }


        if(model instanceof ComposedModel) {
            return ((ComposedModel) model).getAllOf().stream()
                    .filter(c -> c instanceof RefModel)
                    .flatMap(c -> inheritanceId((RefModel)c)
                            .stream().map(s -> id + s)
                    ).collect(Collectors.toSet());
        }

        throw new IllegalArgumentException("model type not supported for " + id);
    }



    private static class RefModelTuple extends Tuple<RefModel, Set<String>> {
        private RefModelTuple(RefModel model, Set<String> keys) {
            super(model, keys);
        }
    }

    private <T extends SchemaNode & DataNodeContainer> Model simple(T toModel) {
        final ModelImpl model = new ModelImpl();
        model.description(desc(toModel));
        model.setProperties(structure(toModel));
        return model;
    }

    @Override
    protected <T extends SchemaNode & DataNodeContainer> Map<String, Property> structure(T node) {
        boolean noUses = node.getUses().isEmpty();
        Predicate<DataSchemaNode> toSimpleProperty = d -> d.isAugmenting() || noUses  || ! d.isAddedByUses();
        return super.structure(node, toSimpleProperty, toSimpleProperty);
    }
}
