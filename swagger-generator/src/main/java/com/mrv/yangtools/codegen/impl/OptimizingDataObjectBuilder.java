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
import com.mrv.yangtools.common.Tuple;
import io.swagger.models.*;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.GroupingEffectiveStatementImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mrv.yangtools.codegen.impl.ModelUtils.isAugmentation;

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

    private static final Predicate<Map<?,?>> hasProperties = hm -> hm != null && !hm.isEmpty();

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

    @SuppressWarnings("unchecked")
    public <T extends SchemaNode & DataNodeContainer> Optional<T> effective(T node) {
        return effectiveNode.stream().filter(n -> {
            return n instanceof SchemaNode && ((SchemaNode) n).getQName().equals(node.getQName());
        }).map(n -> (T)n).findFirst();
    }


    @Override
    public <T extends SchemaNode & DataNodeContainer> String getName(T node) {
        if(isTreeAugmented.test(node)) {
            Optional<T> effective = effective(node);
            return effective.isPresent() ? names.get(effective.get()) : names.get(node);
        } else {
            DataNodeContainer toCheck = original(node) == null ? node : original(node);

            if(isDirectGrouping(toCheck)) {
                return names.get(grouping(toCheck));
            }
        }
        String name = names.get(node);
        if(name == null) {
            name = generateName(node, null, null);
            names.put(node, name);
            log.info("generated name on the fly name for node {} is {}", node.getQName(), name);

        }
        return name;
    }

    private <T extends SchemaNode & DataNodeContainer> T getEffectiveChild(QName name) {
        if(effectiveNode.isEmpty()) return null;
        return effectiveNode.stream().map(n -> n.getDataChildByName(name))
                .filter(n -> n instanceof DataNodeContainer)
                .map(n -> (T)n)
                .findFirst().orElse(null);
    }


    private Stream<GroupingDefinition> groupings(DataNodeContainer node) {
        Set<UsesNode> uses = uses(node);
        //noinspection SuspiciousMethodCalls
        return uses.stream().map(u -> groupings.get(u.getGroupingPath()));
    }

    private GroupingDefinition grouping(DataNodeContainer node) {
        Set<UsesNode> uses = uses(node);
        assert uses.size() == 1;
        //noinspection SuspiciousMethodCalls
        return groupings.get(uses.iterator().next().getGroupingPath());
    }

    /**
     * Is node that has no attributes only single grouping.
     * @param node to check
     * @return <code>true</code> if node is referencing single grouping and has no attributes
     */
    @SuppressWarnings("unchecked")
    private <T extends SchemaNode & DataNodeContainer> boolean isDirectGrouping(DataNodeContainer node) {

        if(node instanceof DataSchemaNode) {
            T n = (T) node;
            T effective = getEffectiveChild(n.getQName());
            if(effective == null) {
                if(! effectiveNode.isEmpty()) {
                    DataNodeContainer first = effectiveNode.getFirst();
                    if(first instanceof SchemaNode && ((SchemaNode) first).getQName().equals(n.getQName())) {
                        effective = (T) first;
                    }
                }
            }
            if(isTreeAugmented.test(effective)) return false;
        }

        if(isAugmented.test(node)) return false;

        Set<UsesNode> uses = uses(node);
        return uses.size() == 1 && node.getChildNodes().stream().allMatch(DataSchemaNode::isAddedByUses);
    }

    @Override
    protected void processNode(DataNodeContainer container, Set<String> cache) {
        final HashSet<String> used = new HashSet<String>(cache);

        DataNodeHelper.stream(container).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> ! names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, null, used);
                    used.add(name);
                    names.put(n, name);
                });
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
        T effectiveNode = (T) getEffectiveChild(node.getQName());

        boolean treeAugmented = isTreeAugmented.test(effectiveNode);

        if(treeAugmented) {
            definitionId = getDefinitionId(effectiveNode);
        }

        log.debug("reference to {}", definitionId);
        RefProperty prop = new RefProperty(definitionId);

        if(treeAugmented) {

            if(! existingModels.containsKey(effectiveNode)) {
                log.debug("adding referenced model {} for node {} ", definitionId, effectiveNode);
                addModel(effectiveNode, getName(effectiveNode));
            } else {
                return prop;
            }
        } else if(existingModel(node) == null) {
            log.debug("adding referenced model {} for node {} ", definitionId, node);
            addModel(node, getName(node));
        }

        return prop;
    }


    @Override
    public <T extends SchemaNode & DataNodeContainer> Model build(T node) {
        if(isTreeAugmented.test(node)) {
            return model(node);
        }
        Model model = existingModel(node);
        if(model == null) {
            model = model(node);
        }

        return model;
    }



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
        if(effectiveNode.isEmpty()) {
            effectiveNode.addFirst(node);
        } else {
            T effectiveChild = getEffectiveChild(node.getQName());
            if(effectiveChild == null) {
                log.warn("no child found with name {}", node.getQName());
                effectiveNode.addFirst(node);
            } else {
                effectiveNode.addFirst(effectiveChild);
            }

        }
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

            LinkedList<RefModel> aModels = new LinkedList<RefModel>();
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
            //add to existing models cache only the augmeted model
            existingModels.put(node, model);
        } else {
            //add to existing models cache mapping between original and used for generation
            // e.g. to properly support case where model was based on grouping
            existingModels.put(toModel, model);
            existingModels.put(node, model);
        }

        verifyModel(node, model);



        Optional<DataNodeContainer> toRemove = effectiveNode.stream().filter(
                n -> n instanceof SchemaNode && ((SchemaNode) n).getQName().equals(node.getQName()))
                .findFirst();

        toRemove.ifPresent(effectiveNode::remove);

        return model;
    }

    Function<RefModel, Model> fromReference = ref -> swagger.getDefinitions().get(ref.getSimpleRef());

    private <T extends SchemaNode & DataNodeContainer> void verifyModel(T node, Model model) {
        if(model instanceof ComposedModel) {
            List<RefModel> refModels = ((ComposedModel) model).getAllOf().stream()
                    .filter(m -> m instanceof RefModel)
                    .map(m -> (RefModel)m)
                    .collect(Collectors.toList());
            if(refModels.size() <= 1) {
                if(refModels.isEmpty() || !isAugmentation(fromReference.apply(refModels.get(0)))) {

                    boolean emptyAttributes = ((ComposedModel) model).getAllOf().stream().filter(m -> m instanceof ModelImpl)
                            .map(m -> !hasProperties.test(m.getProperties())).findFirst().orElse(false);

                    if (emptyAttributes) {
                        log.warn("Incorrectly constructed model {}, hierarchy can be flattened with postprocessor", node.getQName());
                    }
                }
            }
        }
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
    public <T extends SchemaNode & DataNodeContainer> void addModel(T node, String name) {
        super.addModel(node, name);
    }


    private static class GroupingInfo {
        final Set<GroupingDefinition> models;
        final Set<QName> attributes;

        private GroupingInfo() {
            attributes = new HashSet<>();
            models = new HashSet<>();
        }
        private void addUse(GroupingInfo u) {
            attributes.addAll(u.attributes);
            models.addAll(u.models);
        }
    }

    private GroupingInfo traverse(GroupingDefinition def) {
        GroupingInfo info = groupings(def).map(this::traverse)
                .reduce(new GroupingInfo(), (o, cgi) -> {
                    o.addUse(cgi);
                    return o;
                });
        boolean tryToReuseGroupings = info.attributes.isEmpty();
        if(tryToReuseGroupings) {
            boolean noneAugmented = def.getChildNodes().stream().filter(c -> !c.isAddedByUses())
                    .allMatch(c -> {
                        DataSchemaNode effectiveChild = getEffectiveChild(c.getQName());
                        return ! OptimizingDataObjectBuilder.isAugmented.test((DataNodeContainer) effectiveChild);
                    });
            if(noneAugmented) {
                String groupingIdx = getDefinitionId(def);
                log.debug("found grouping id {} for {}", groupingIdx, def.getQName());
                info.models.clear();
                info.models.add(def);
            } else tryToReuseGroupings = false;

        }
        if(! tryToReuseGroupings) {
            def.getChildNodes().stream().filter(c -> !c.isAddedByUses())
                    .forEach(c -> info.attributes.add(c.getQName()));
        }
        return info;
    }



    private Model composed(DataNodeContainer node) {
        ComposedModel newModel = new ComposedModel();

        final Set<QName> fromAugmentedGroupings = new HashSet<>();
        final List<RefModel> models = new LinkedList<>();

        uses(node).forEach(u -> {
            GroupingDefinition grouping = groupings.get(u.getGroupingPath());
            GroupingInfo info = traverse(grouping);
            info.models.forEach(def -> {
                String groupingIdx = getDefinitionId(def);
                log.debug("adding grouping {} to composed model", groupingIdx);
                RefModel refModel = new RefModel(groupingIdx);

                if (existingModel(def) == null) {
                    log.debug("adding model {} for grouping", groupingIdx);
                    addModel(def, getName(def));
                }
                models.add(refModel);
            });
            fromAugmentedGroupings.addAll(new ArrayList<>(info.attributes));
        });

        List<RefModel> optimizedModels = optimizeInheritance(models);

        SchemaNode doc = null;
        if(node instanceof SchemaNode) {
            doc = (SchemaNode) node;
        }

        if(optimizedModels.size() > 1 && doc != null) {
            log.warn("Multiple inheritance for {}", doc.getQName());
        }
        // XXX this behavior might be prone to future changes to Swagger model
        // currently interfaces are stored as well in allOf property
        newModel.setInterfaces(optimizedModels);
        if(!models.isEmpty())
            newModel.parent(models.get(0));

        // because of for swagger model order matters we need to referencing attributes at the end
        final ModelImpl attributes = new ModelImpl();
        if(doc != null)
            attributes.description(desc(doc));
        attributes.setProperties(structure(node, n -> fromAugmentedGroupings.contains(n.getQName()) ));
        //attributes.setDiscriminator("objType");
        attributes.setType("object");
        if(hasProperties.test(attributes.getProperties())) {
            newModel.child(attributes);
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
        log.debug("added object type for {}", toModel.toString());
        model.setType("object");
        model.setProperties(structure(toModel));
        return model;
    }

    @Override
    protected Map<String, Property> structure(DataNodeContainer node) {
        Predicate<DataSchemaNode> toSimpleProperty = d ->  !d.isAugmenting() && ! d.isAddedByUses();
        return super.structure(node, toSimpleProperty, toSimpleProperty);
    }

    protected Map<String, Property> structure(DataNodeContainer node, Predicate<DataSchemaNode> includeAttributes) {
        Predicate<DataSchemaNode> toSimpleProperty = d ->  !d.isAugmenting() && ! d.isAddedByUses();
        return super.structure(node, toSimpleProperty.or(includeAttributes), toSimpleProperty.or(includeAttributes));
    }
}
