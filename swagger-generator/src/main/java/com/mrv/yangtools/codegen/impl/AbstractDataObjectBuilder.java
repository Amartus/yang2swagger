/*
 *   Copyright (c) 2016-2024.  MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Christopher Murch <cmurch@mrv.com>
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.DataObjectBuilder;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Swagger;
import io.swagger.models.properties.*;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.util.ContainerSchemaNodes;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.stmt.ChoiceEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mrv.yangtools.common.BindingMapping.getClassName;
import static com.mrv.yangtools.common.BindingMapping.nameToPackageSegment;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractDataObjectBuilder implements DataObjectBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbstractDataObjectBuilder.class);


    protected final Swagger swagger;
    protected final TypeConverter converter;
    protected final EffectiveModelContext ctx;
    protected final ModuleUtils moduleUtils;
    protected final Map<SchemaNode, String> names;
    private final HashMap<QName, String> generatedEnums;
    private final HashMap<DataNodeContainer, String> orgNames;

    /** {@code true} when separate {@code <name>Config} write models should be generated. */
    private boolean splitConfigState = false;
    /** {@code true} while {@link #buildWriteModel(SchemaNode)} is in progress - drives child inlining in {@link #prop(DataSchemaNode)}. */
    private boolean buildingWriteModel = false;
    /** Names of write models already registered in the swagger definitions (guards against re-building / cycles). */
    private final Set<String> writeModels = new HashSet<>();
    /** Caches whether a subtree contains any {@code config false} node. */
    private final Map<DataNodeContainer, Boolean> stateNodeCache = new HashMap<>();
    /** Accepts only {@code config true} nodes - used to filter children out of write models. */
    protected final Predicate<DataSchemaNode> isConfig = DataSchemaNode::isConfiguration;

    protected final static Function<DataNodeContainer, Collection<? extends AugmentationSchemaNode>> augmentations = node -> {
        if(node instanceof AugmentationTarget) {
            Collection<? extends AugmentationSchemaNode> res = ((AugmentationTarget) node).getAvailableAugmentations();
            if(res != null) return res;
        }
        return Collections.emptySet();
    };

    protected final static Predicate<DataNodeContainer> isAugmented = n -> !augmentations.apply(n).isEmpty();

    protected final Predicate<DataNodeContainer> isTreeAugmented = n ->  n != null && (isAugmented.test(n) || n.getChildNodes().stream()
            .filter(c -> c instanceof DataNodeContainer)
            .anyMatch(c -> this.isTreeAugmented.test((DataNodeContainer) c)));

    public AbstractDataObjectBuilder(EffectiveModelContext ctx, Swagger swagger, TypeConverter converter) {
        this.names = new HashMap<>();
        this.converter = converter;
        converter.setDataObjectBuilder(this);
        this.swagger = swagger;
        this.ctx = ctx;
        this.moduleUtils = new ModuleUtils(ctx);
        this.generatedEnums = new HashMap<>();
        this.orgNames = new HashMap<>();

        if(swagger.getDefinitions() == null) {
            swagger.setDefinitions(new LinkedHashMap<>());
        }
    }

    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> String getDefinitionRef(T node) {
        return DEF_PREFIX + getName(node);
    }

    @Override
    public void setSplitConfigState(boolean enabled) {
        this.splitConfigState = enabled;
    }

    @Override
    public boolean isSplitConfigState() {
        return splitConfigState;
    }

    /**
     * {@inheritDoc}
     * <p>
     * When split mode is on and {@code node}'s subtree carries {@code config false} data, a
     * dedicated {@code <name>Config} model (state nodes pruned at every level) is registered
     * lazily and its reference returned. Otherwise the regular read reference is returned.
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> String getWriteDefinitionRef(T node) {
        if(!splitConfigState || !hasStateNodes(node)) {
            return getDefinitionRef(node);
        }
        String writeName = getName(node) + WRITE_MODEL_SUFFIX;
        if(writeModels.add(writeName)) {
            log.debug("building write model {}", writeName);
            swagger.addDefinition(writeName, buildWriteModel(node));
        }
        return DEF_PREFIX + writeName;
    }

    /**
     * Build a model holding only the {@code config true} part of a node's subtree. Config-false
     * children are dropped; config-true children that still contain state descendants are inlined
     * (recursively pruned) so the resulting request body never references state data.
     * @param node node to build the write model for
     * @return write model
     */
    protected <T extends SchemaNode & DataNodeContainer> Model buildWriteModel(T node) {
        ModelImpl model = new ModelImpl();
        model.description(desc(node));
        model.setType("object");
        boolean previous = buildingWriteModel;
        buildingWriteModel = true;
        try {
            model.setProperties(structure(node, isConfig, isConfig));
        } finally {
            buildingWriteModel = previous;
        }
        return model;
    }

    /**
     * Tells whether a subtree contains at least one {@code config false} (state) node.
     * @param node subtree root
     * @return {@code true} if any descendant is state data
     */
    protected boolean hasStateNodes(DataNodeContainer node) {
        Boolean cached = stateNodeCache.get(node);
        if(cached != null) return cached;
        boolean result = containsStateNode(node);
        stateNodeCache.put(node, result);
        return result;
    }

    private boolean containsStateNode(DataNodeContainer node) {
        for(DataSchemaNode child : node.getChildNodes()) {
            if(!child.isConfiguration()) return true;
            if(child instanceof ChoiceSchemaNode) {
                for(CaseSchemaNode aCase : ((ChoiceSchemaNode) child).getCases()) {
                    if(containsStateNode(aCase)) return true;
                }
            } else if(child instanceof DataNodeContainer) {
                if(containsStateNode((DataNodeContainer) child)) return true;
            }
        }
        return false;
    }

    /**
     * Traverse model to collect all verbs from YANG nodes that will constitute Swagger models
     * @param module to traverse
     */
    @Override
    public void processModule(Module module) {
        HashSet<String> cache = new HashSet<>(names.values());
        log.debug("processing data nodes defined in {}", module.getName());
        processNode(module, cache);

        log.debug("processing rpcs defined in {}", module.getName());
        module.getRpcs()
                        .forEach(rcp -> processNode(ContainerSchemaNodes.forRPC(rcp), null, cache));
        log.debug("processing augmentations defined in {}", module.getName());
        module.getAugmentations().forEach(r -> processNode(r, cache));
    }

    protected <T extends ContainerLike> void processNode(T container, String proposedName, Set<String> cache) {
        if(container == null) return;
        String name = generateName(container, null);
        names.put(container, name);

        processNode(container, cache);
    }

    protected void processNode(DataNodeContainer container, Set<String> cache) {
        log.debug("DataNodeContainer string: {}", container.toString());
        DataNodeHelper.stream(container).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> ! names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, null);
                    names.put(n, name);
                });
    }

    protected DataNodeContainer original(DataNodeContainer node) {
        DataNodeContainer result = null;
        DataNodeContainer tmp = node;
        do {
            if(tmp instanceof DerivableSchemaNode) {
                Optional<? extends SchemaNode> original = ((DerivableSchemaNode) tmp).getOriginal();
                tmp = null;
                var dnc = original
                        .filter(o -> o instanceof DataNodeContainer)
                        .map(o -> (DataNodeContainer) o);
                if(dnc.isPresent()) {
                    result = dnc.get();
                    tmp = result;
                }
            } else {
                tmp = null;
            }
        } while (tmp != null);

        return result;
    }

    protected String generateName(SchemaNode node, String proposedName) {
        if(node instanceof DataNodeContainer) {
            DataNodeContainer original = null;
            if(! isTreeAugmented.test((DataNodeContainer) node)) {
                original = original((DataNodeContainer) node);
            }

            if(original != null) {
                if(! orgNames.containsKey(original)) {
                    String name = generateName((SchemaNode)original, proposedName);
                    orgNames.put(original, name);
                } else {
                    log.debug("reusing original definition to get name for {}", node.getQName());
                }

                return orgNames.get(original);
            } else {
                DataNodeContainer t = (DataNodeContainer) node;
                if(orgNames.containsKey(t)) {
                    return orgNames.get(t);
                }
            }
        }

        String modulePrefix =  nameToPackageSegment(moduleUtils.toModuleName(node.getQName().getModule()));
        if(proposedName != null) {
            return modulePrefix + "." + getClassName(proposedName);
        }

        String name = getClassName(node.getQName());
        final Iterable<QName> path = node.getPath().getParent().getPathFromRoot();
        if(path == null || !path.iterator().hasNext()) {
            log.debug("generatedName: {}", modulePrefix + "." + name);
            return modulePrefix + "." + name;
        }
        String pkg = StreamSupport.stream(path.spliterator(), false).map(n -> getClassName(n.getLocalName()).toLowerCase()).collect(Collectors.joining("."));
        log.debug("generatedName: {}", modulePrefix + "." + pkg + "." + name);
        return modulePrefix + "." + pkg + "." + name;
    }

    /**
     * Convert leaf-list to swagger property
     * @param llN leaf-list
     * @return property
     */
    protected Property getPropertyByType(LeafListSchemaNode llN) {
        return converter.convert(llN.getType(), llN);
    }

    /**
     * Convert leaf to swagger property
     * @param lN leaf
     * @return property
     */
    protected Property getPropertyByType(LeafSchemaNode lN) {

        final Property property = converter.convert(lN.getType(), lN);
        property.setDefault(lN.getType().getDefaultValue().map(Object::toString).orElse(null));

        return property;
    }


    protected Map<String, Property> structure(DataNodeContainer node) {
        return structure(node,  x -> true, x -> true);
    }


    protected Map<String, Property> structure(DataNodeContainer node, Predicate<DataSchemaNode> acceptNode, Predicate<DataSchemaNode> acceptChoice) {

        Predicate<DataSchemaNode> choiceP = c -> c instanceof ChoiceSchemaNode;

        // due to how inheritance is handled in yangtools the localName node collisions might appear
        // thus we need to apply collision strategy to override with the last attribute available
        Map<String, Property> properties = node.getChildNodes().stream()
                .filter(choiceP.negate().and(acceptNode)) // choices handled elsewhere
                .map(this::prop).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        Map<String, Property> choiceProperties = node.getChildNodes().stream()
                .filter(choiceP.and(acceptChoice)) // handling choices
                .flatMap(c -> {
                    ChoiceSchemaNode choice = (ChoiceSchemaNode) c;
                    return choice.getCases().stream()
                            .flatMap(_case -> _case.getChildNodes().stream().map(sc -> {
                                Pair prop = prop(sc);
                                assignCaseMetadata(prop.property, choice, _case);
                                return prop;
                            }));
                }).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        HashMap<String, Property> result = new HashMap<>();

        result.putAll(properties);
        result.putAll(choiceProperties);
        return result;
    }

    protected Pair prop(DataSchemaNode node) {
        final String propertyName = getPropertyName(node);

        Property prop = null;

        if (node instanceof LeafListSchemaNode) {
            LeafListSchemaNode ll = (LeafListSchemaNode) node;
            prop = new ArrayProperty(getPropertyByType(ll));
        } else if (node instanceof LeafSchemaNode) {
            LeafSchemaNode lN = (LeafSchemaNode) node;
            prop = getPropertyByType(lN);
        } else if (node instanceof ContainerLike) {
            prop = writeAwareRefOrStructure((ContainerLike) node);
        } else if (node instanceof ListSchemaNode) {
            prop = new ArrayProperty().items(writeAwareRefOrStructure((ListSchemaNode) node));
        } else if (node instanceof ChoiceEffectiveStatement) {
            log.warn("direct choice not supported yey for {}", node.getQName());
            prop = new ObjectProperty();
        } else if (node instanceof AnyxmlSchemaNode) {
            log.warn("generating swagger string property for any schema type for {}", node.getQName());
            prop = new StringProperty();
        }



        if (prop != null) {
            prop.setDescription(desc(node));
            if (splitConfigState && !node.isConfiguration()) {
                // OpenAPI 2.0 hint for clients that honour it; the *Config write model is the primary fix
                prop.setReadOnly(true);
            }
        } else {
            log.warn("{} type not supported for {}", node.getClass().getName(), node.getQName());
        }

        return new Pair(propertyName, prop);
    }

    /**
     * Resolve a container/list child property. While building a write model, a config-true child
     * that still holds state descendants is inlined with its state nodes pruned; otherwise the
     * regular {@link #refOrStructure(DataSchemaNode)} reference is used.
     */
    private <T extends DataSchemaNode & DataNodeContainer> Property writeAwareRefOrStructure(T node) {
        if (buildingWriteModel && hasStateNodes(node)) {
            return new ObjectProperty(structure(node, isConfig, isConfig));
        }
        return refOrStructure(node);
    }

    public String getPropertyName(DataSchemaNode node) {
        //return BindingMapping.getPropertyName(node.getQName().getLocalName());
        String name = node.getQName().getLocalName();
        if(node.isAugmenting()) {
            name = moduleName(node) + ":" + name;
        }
        return name;
    }

    private String moduleName(DataSchemaNode node) {
        Module module = ctx.findModule(QNameModule.create(node.getQName().getNamespace(), node.getQName().getRevision())).orElseThrow();
        return module.getName();
    }

    protected abstract <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node);

    private static void assignCaseMetadata(Property property, ChoiceSchemaNode choice, CaseSchemaNode aCase) {
        String choiceName = choice.getQName().getLocalName();
        String caseName = aCase.getQName().getLocalName();

        ((AbstractProperty) property).setVendorExtension("x-choice", choiceName + ":" + caseName);
    }

    /**
     * Add model to referenced swagger for given node. All related models are added as well if needed.
     * @param node for which build a node
     * @param modelName model name
     * @param <T> type of the node
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> void addModel(T node, String modelName) {


        Model model = build(node);


        if(swagger.getDefinitions() != null && swagger.getDefinitions().containsKey(modelName)) {
            if(model.equals(swagger.getDefinitions().get(modelName))) {
                return;
            }
            log.warn("Overriding model {} with node {}", modelName, node.getQName());
        }

        swagger.addDefinition(modelName, model);
    }

    public <T extends SchemaNode & DataNodeContainer> void addModel(T node) {
        addModel(node, getName(node));
    }


    @Override
    public String addModel(EnumTypeDefinition enumType) {
        QName qName = enumType.getQName();
        
        //inline enumerations are a special case that needs extra enumeration
        if(qName.getLocalName().equals("enumeration") && enumType.getBaseType() == null) {
        	qName = QName.create(qName, enumType.getPath().getParent().getLastComponent().getLocalName() + "-" + qName.getLocalName());
        }

        if(! generatedEnums.containsKey(qName)) {
            log.debug("generating enum model for {}",  qName);
            String name = getName(qName);
            ModelImpl enumModel = build(enumType, qName);
            swagger.addDefinition(name, enumModel);
            generatedEnums.put(qName, DEF_PREFIX + name);
        } else {
            log.debug("reusing enum model for {}", enumType.getQName());
        }
        return generatedEnums.get(qName);
    }

    protected ModelImpl build(EnumTypeDefinition enumType, QName qName) {
        ModelImpl model = new ModelImpl();
        model.setEnum(enumType.getValues().stream()
                .map(EnumTypeDefinition.EnumPair::getName).collect(Collectors.toList()));
        model.setType("string");
        model.setReference(getName(qName));
        return model;
    }

    protected String getName(QName qname) {
        String modulePrefix =  nameToPackageSegment(moduleUtils.toModuleName(qname.getModule()));
        String name = modulePrefix + "." + getClassName(qname);

        String candidate = name;

        int idx = 1;
        while(generatedEnums.containsValue(DEF_PREFIX + candidate)) {
            log.warn("Name {} already defined for enum. generating random postfix", candidate);
            candidate = name + idx;
        }
        return candidate;
    }

    protected String desc(DocumentedNode node) {
        return  node.getReference().isEmpty() ? node.getDescription().orElse(null) :
                node.getDescription().orElse(null) + " REF:" + node.getReference();
    }

    protected static class Pair {
        final protected String name;
        final protected Property property;

        protected Pair(String name, Property property) {
            this.name = name;
            this.property = property;
        }
    }
}
