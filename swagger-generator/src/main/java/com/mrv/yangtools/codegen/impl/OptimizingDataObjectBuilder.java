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

import com.mrv.yangtools.common.BindingMapping;
import io.swagger.models.*;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.GroupingEffectiveStatementImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.Objects;
import java.util.function.Function;
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

    private Map<Object, Model> existingModels;
    private final GroupingHierarchyHandler groupingHierarchyHandler;
    private Map<Object, Set<UsesNode>> usesCache;

    private final Deque<DataNodeContainer> effectiveNode;

    public OptimizingDataObjectBuilder(SchemaContext ctx, Swagger swagger, TypeConverter converter) {
        super(ctx, swagger, converter);
        groupings = new HashMap<>();
        existingModels = new HashMap<>();
        usesCache = new HashMap<>();
        groupingHierarchyHandler = new GroupingHierarchyHandler(ctx);
        effectiveNode = new LinkedList<>();

        Set<Module> allModules = ctx.getModules();
        HashSet<String> names = new HashSet<>();
        allModules.forEach(m -> processGroupings(m, names));
    }


    @Override
    public <T extends SchemaNode & DataNodeContainer> String getName(T node) {
        if(isAugmented.test(node)) {
            return names.get(node);
        } else {
            DataNodeContainer toCheck = original(node) == null ? node : original(node);

            if(isDirectGrouping(toCheck)) {
                return names.get(grouping(toCheck));
            }
        }
        return names.get(node);
    }

    private static Function<DataNodeContainer, Set<AugmentationSchema>> augmentations = node -> {
        if(node instanceof AugmentationTarget) {
            Set<AugmentationSchema> res = ((AugmentationTarget) node).getAvailableAugmentations();
            if(res != null) return res;
        }
        return Collections.emptySet();
    };

    private static Predicate<DataNodeContainer> isAugmented = n -> !augmentations.apply(n).isEmpty();

    private GroupingDefinition grouping(DataNodeContainer node) {
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
    private  boolean isDirectGrouping(DataNodeContainer node) {
        if (isAugmented.test(node)) return false;

        Set<UsesNode> uses = uses(node);
        return uses.size() == 1 && node.getChildNodes().stream().filter(n -> !n.isAddedByUses()).count() == 0;
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
    }

    protected void processGroupings(DataNodeContainer container, Set<String> cache) {
        DataNodeHelper.stream(container).filter(n -> n instanceof GroupingDefinition)
                .map(n -> (GroupingDefinition)n)
                .forEach(n -> {
                    String gName = generateName(n, null, cache);
                    if(names.values().contains(gName)) {
                        //no type compatibility check at the moment thus this piece of code is prone to changes in parser

                        boolean differentDeclaration = groupings.values().stream().map(g -> ((GroupingEffectiveStatementImpl) g).getDeclared())
                                .noneMatch(g -> g.equals(((GroupingEffectiveStatementImpl) n).getDeclared()));
                        if(differentDeclaration) {
                            gName = "G" + gName;
                        }
                    }

                    names.put(n, gName);
                    groupings.put(n.getPath(), n);
                });
    }



    @SuppressWarnings("unchecked")
    @Override
    protected <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node) {
        String definitionId = getDefinitionId(node);
        if(! effectiveNode.isEmpty()) {
            T effectiveNode = (T) this.effectiveNode.peekFirst().getDataChildByName(node.getQName());
            if(isAugmented.test(effectiveNode)) {
                definitionId = getDefinitionId(effectiveNode);
            }
        }



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
        if(isAugmented.test(node)) {
            return model(node);
        }
        Model model = existingModel(node);
        if(model == null) {
            model = model(node);
        }

        return model;
    }

    @SuppressWarnings("unchecked")
    private DataNodeContainer original(DataNodeContainer node) {
        DataNodeContainer result = null;
        DataNodeContainer tmp = node;
        do {
            if(tmp instanceof DerivableSchemaNode) {
                com.google.common.base.Optional<? extends SchemaNode> original = ((DerivableSchemaNode) tmp).getOriginal();
                tmp = null;
                if(original.isPresent() && original.get() instanceof DataNodeContainer) {
                    result = (DataNodeContainer) original.get();
                    tmp = result;
                }
            } else {
                tmp = null;
            }
        } while (tmp != null);

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<DataNodeContainer> findRelatedNodes(DataNodeContainer node) {
        ArrayList<DataNodeContainer> result = new ArrayList<>();
        result.add(node);
        DataNodeContainer candidate = original(node);
        if(candidate != null) {
            result.add(candidate);
        } else {
            candidate = node;
        }

        if(isDirectGrouping(candidate)) {
            result.add(grouping(candidate));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T extends DataNodeContainer> Model existingModel(T node) {
        return findRelatedNodes(node).stream().map(n -> existingModels.get(n))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /**
     * Create model for a node with zero of single uses
     * @param c to process
     * @return model or null in case more than one grouping is used
     */

    private Model fromContainer(DataNodeContainer c) {
        boolean simpleModel;
        DataNodeContainer tmp;
        DataNodeContainer toModel = c;
        do {
            tmp = toModel;
            simpleModel = uses(toModel).isEmpty() || isDirectGrouping(toModel);
            toModel = isDirectGrouping(toModel) ?  grouping(toModel) : toModel;
        } while(tmp != toModel && simpleModel);

        return simpleModel ? simple(toModel) : composed(toModel);
    }

    private Model fromAugmentation(AugmentationSchema augmentation) {

        Model model = fromContainer(augmentation);
        final Model toCheck = model;

        String existingId = swagger.getDefinitions().entrySet().stream().filter(e -> e.getValue().equals(toCheck)).map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if(existingId != null) {
            RefModel ref = new RefModel(existingId);
            ComposedModel composedModel = new ComposedModel();
            composedModel.setChild(ref);
            model = composedModel;
        }

        HashMap<String, String> properties = new HashMap<>();

        if(augmentation instanceof NamespaceRevisionAware) {
            URI uri = ((NamespaceRevisionAware) augmentation).getNamespace();
            properties.put("namespace", uri.toString());
            properties.put("prefix", moduleUtils.toModuleName(uri));
            model.getVendorExtensions().put("x-augmentation", properties);
        }


        return model;
    }

    @SuppressWarnings("unchecked")
    private <T extends SchemaNode & DataNodeContainer> Model model(T node) {
        effectiveNode.addFirst(node);
        DataNodeContainer original = original(node);
        T toModel =  node;
        if(original instanceof SchemaNode) {
            toModel = (T) original;
        }

        Model model = fromContainer(toModel);

        assert original == null || !isAugmented.test(original);
        if(isAugmented.test(node)) {
            String modelName = getName(toModel);
            int lastSegment = modelName.lastIndexOf(".") + 1;
            assert lastSegment > 0 : "Expecting name convention from name generator";
            modelName = modelName.substring(lastSegment);

            log.debug("processing augmentations for {}", node.getQName().getLocalName());
            //TODO order models
            List<Model> models = augmentations.apply(node).stream().map(this::fromAugmentation).collect(Collectors.toList());

            ComposedModel augmented = new ComposedModel();
            if(model instanceof ComposedModel) {
                augmented = (ComposedModel) model;
            } else {
                augmented.setDescription(model.getDescription());
                model.setDescription("");
                augmented.child(model);
            }

            LinkedList<RefModel> aModels = new LinkedList();
            if(augmented.getInterfaces() != null) {
                aModels.addAll(augmented.getInterfaces());
            }
            int idx = 1;
            for(Model m : models) {
                Map<String, String> prop = (Map<String, String>) m.getVendorExtensions().getOrDefault("x-augmentation", Collections.emptyMap());
                String pkg = BindingMapping.nameToPackageSegment(prop.get("prefix"));
                String augName = pkg + "." + modelName + "Augmentation" + idx;
                swagger.getDefinitions().put(augName, m);
                aModels.add(new RefModel("#/definitions/"+augName));
                idx++;

            }
            augmented.setInterfaces(aModels);
            model = augmented;
        }

        existingModels.put(toModel, model);
        existingModels.put(node, model);
        effectiveNode.removeFirst();
        return model;
    }

    private Set<UsesNode> uses(DataNodeContainer toModel) {
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

            return result.stream().filter(o -> ! o.equals(r))
                    .noneMatch(o -> groupingHierarchyHandler.isParent(rName, o.getGroupingPath().getLastComponent()));
        }).collect(Collectors.toSet());
    }

    @Override
    public <T extends SchemaNode & DataNodeContainer> void addModel(T node) {
        super.addModel(node);
    }

    private Model composed(DataNodeContainer node) {
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


        SchemaNode doc = null;
        if(node instanceof SchemaNode) {
            doc = (SchemaNode) node;
        }

        if(models.size() > 1 && doc != null) {
            log.warn("Multiple inheritance for {}", doc.getQName());
        }
        // XXX this behavior might be prone to future changes to Swagger model
        // currently interfaces are stored as well in allOf property
        newModel.setInterfaces(models);
        if(!models.isEmpty())
            newModel.parent(models.get(0));

        final ModelImpl attributes = new ModelImpl();
        if(doc != null)
            attributes.description(desc(doc));
        attributes.setProperties(structure(node ));
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

    private  Model simple(DataNodeContainer toModel) {
        final ModelImpl model = new ModelImpl();
        if(model instanceof DocumentedNode) {
            model.description(desc((DocumentedNode) toModel));
        }
        model.setProperties(structure(toModel));
        return model;
    }

    @Override
    protected Map<String, Property> structure(DataNodeContainer node) {
        Predicate<DataSchemaNode> toSimpleProperty = d ->  !d.isAugmenting() && ! d.isAddedByUses();
        return super.structure(node, toSimpleProperty, toSimpleProperty);
    }
}
