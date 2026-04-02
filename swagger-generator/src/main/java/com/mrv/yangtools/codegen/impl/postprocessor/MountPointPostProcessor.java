package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.ComposedModel;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Operation;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import com.mrv.yangtools.codegen.impl.ModuleUtils;
import com.mrv.yangtools.codegen.impl.DataNodeHelper;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.DataObjectBuilder;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.MountPointTarget;
import com.mrv.yangtools.codegen.MountPointMappings;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;
import org.opendaylight.yangtools.yang.data.util.ContainerSchemaNodes;

/**
 * Lightweight postprocessor that attaches mount definitions to target models.
 */
public class MountPointPostProcessor implements java.util.function.Consumer<Swagger> {
    private static final Logger log = LoggerFactory.getLogger(MountPointPostProcessor.class);

    /** Encapsulates all reflection-based probing of ODL effective-statement internals. */
    private final EffectiveStatementReflectionHelper reflectionHelper = new EffectiveStatementReflectionHelper();

    private final MountPointMappings mappings;
    private final EffectiveModelContext ctx;
    private final ModuleUtils moduleUtils;
    private final DataObjectRepo dataRepo;
    private final Map<String, Swagger> moduleSwaggerCache = new HashMap<>();

    public MountPointPostProcessor(MountPointMappings mappings, EffectiveModelContext ctx, ModuleUtils moduleUtils, DataObjectRepo dataRepo) {
        this.mappings = mappings == null ? new MountPointMappings(Collections.emptyMap()) : mappings;
        this.ctx = ctx;
        this.moduleUtils = moduleUtils;
        this.dataRepo = dataRepo;
    }

    // Collect mount-point labels across all modules and return mapping label -> list of DataNodeContainer nodes
    private Map<String, List<DataNodeContainer>> getMountPointFromModules() {
        Map<String, List<DataNodeContainer>> nodesByLabel = new HashMap<>();
        ctx.getModules()
                .forEach(module -> collectMountPointsFromContainer(module, nodesByLabel));
        return nodesByLabel;
    }

    // Scan provided container (module/container/list) for nodes and try to find mount-point label on each
    private void collectMountPointsFromContainer(DataNodeContainer container, Map<String, List<DataNodeContainer>> nodesByLabel) {
        log.debug("Scanning container for mount-points: {}", container);

        // DataNodeHelper.stream(container) yields schema nodes (recursively); check each for mount-point extension
        DataNodeHelper.stream(container)
                .forEach(node -> {
                    String label = findMountPointLabel(node);
                    if (label != null && !label.isEmpty()) {
                        log.info("Found mount-point label '{}' in node {}", label, node);
                        // ensure we store the container node (only Container/List/Module/Grouping are DataNodeContainer)
                        if (node instanceof DataNodeContainer) {
                            nodesByLabel.computeIfAbsent(label, k -> new ArrayList<>()).add((DataNodeContainer) node);
                        } else {
                            // if not a container, associate with the parent container passed to this method
                            nodesByLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(container);
                        }
                    }
                });
    }

    @Override
    public void accept(Swagger swagger) {
        if(swagger.getDefinitions() == null || swagger.getDefinitions().isEmpty()) return;

        // build a set of candidate refs based on provided module:grouping strings
        Map<String, String> candidateDefs = buildCandidateDefinitions(swagger);

        // collect nodes by mount-point label
        Map<String, List<DataNodeContainer>> nodesByLabel = getMountPointFromModules();

        if(nodesByLabel.isEmpty()) {
            log.info("No mount-point extensions found in model");
            return;
        }

        // for each found mount label, look up CLI mapping and attach definitions to corresponding swagger models
        for(Map.Entry<String, List<DataNodeContainer>> entry : nodesByLabel.entrySet()) {
            String label = entry.getKey();
            List<MountPointTarget> targets = mappings.getTargets(label);
            if(targets.isEmpty()) continue;

            // resolve mapped module:grouping entries to definition refs using context and dataRepo
            List<RefModel> refModels = new ArrayList<>();
            for(MountPointTarget target : targets) {
                // module-only case: module is null, check if name resolves to a known module
                if(target.getModule() == null) {
                    Optional<Module> mod = findModuleByName(target.getName());
                    if(mod.isPresent()) {
                        // gather refs from the whole module
                        List<RefModel> moduleRefs = resolveModuleMappings(mod.get().getName(), swagger);
                        addUniqueRefModels(refModels, moduleRefs);
                        // attach RPCs from this module to the mount nodes
                        attachModuleRpcsToMount(mod.get(), entry.getValue(), swagger);
                        attachModuleDataPathsToMount(mod.get(), entry.getValue(), swagger);
                        continue;
                    } else {
                        throw new IllegalArgumentException(target.getName() + " does not exist, check Your configuration & spelling");
                    }
                }

                String modulePart = target.getModule() != null ? target.getModule() : "";
                String namePart = target.getName();

                // If mapping explicitly references a module (module:...), ensure RPCs from that module are attached to this mount
                if(!modulePart.isEmpty()) {
                    Optional<Module> moduleRef = findModuleByName(modulePart);
                    if(moduleRef.isPresent()) {
                        try {
                            attachModuleRpcsToMount(moduleRef.get(), entry.getValue(), swagger);
                            attachModuleDataPathsToMount(moduleRef.get(), entry.getValue(), swagger);
                        } catch (Exception e) {
                            log.debug("Attaching RPCs for module {} failed: {}", modulePart, e.toString());
                        }
                    }
                }

                String defRef = null;
                Object resolvedNode = null;
                // Three-tier fallback resolution order:
                //   1) Exact match on grouping QName local name (and optional module).
                //   2) Exact match on top-level container/list QName local name (and optional module).
                //   3) Fuzzy substring match against all existing swagger definition keys.
                //      WARNING: tier 3 uses a substring (contains) check, so a target named e.g.
                //      "config" would also match "specific-config". This may silently resolve
                //      to the wrong definition. If you see unexpected mount-point attachments,
                //      check the WARN log emitted when this heuristic fires.

                // 1) try groupings - exact QName match
                for(GroupingDefinition g : ctx.getGroupings()) {
                    String gLocal = g.getQName().getLocalName();
                    String gModule = moduleUtils.toModuleName(g);
                    if(gLocal.equals(namePart) && (modulePart.isEmpty() || gModule.equals(modulePart))) {
                        try { defRef = dataRepo.getDefinitionRef(g); resolvedNode = g; } catch (Exception e) { defRef = null; resolvedNode = null; }
                        if(defRef != null) break;
                    }
                }
                // 2) try containers/lists - exact QName match
                if(defRef == null) {
                    Iterator<org.opendaylight.yangtools.yang.model.api.SchemaNode> it = DataNodeHelper.stream(ctx)
                            .filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                            .iterator();
                    while(it.hasNext()) {
                        org.opendaylight.yangtools.yang.model.api.SchemaNode candidate = it.next();
                        if(!(candidate instanceof DataSchemaNode)) continue;
                        DataSchemaNode ds = (DataSchemaNode) candidate;
                        String local = ds.getQName().getLocalName();
                        String candModule = moduleUtils.toModuleName(ds);
                        if(local.equals(namePart) && (modulePart.isEmpty() || candModule.equals(modulePart))) {
                            try { defRef = resolveDefinition((DataNodeContainer)ds); resolvedNode = ds; } catch (Exception e) { defRef = null; resolvedNode = null; }
                            if(defRef != null) break;
                        }
                    }
                }
                // 3) fallback: fuzzy substring heuristic against swagger definitions (may match wrong definition - see comment above)
                if(defRef == null && !candidateDefs.isEmpty()) {
                    String lower = namePart.toLowerCase();
                    Optional<String> match = candidateDefs.keySet().stream().filter(k -> k.contains(lower) && (modulePart.isEmpty() || k.contains(modulePart.toLowerCase()))).findFirst();
                    if(match.isPresent()) {
                        defRef = "#/definitions/" + candidateDefs.get(match.get());
                        log.warn("Mount-point target '{}{}' resolved via fuzzy substring match to definition '{}'. "
                                        + "This heuristic uses contains() and may have matched the wrong definition. "
                                        + "Consider using an exact module:grouping mapping to avoid ambiguity.",
                                modulePart.isEmpty() ? "" : modulePart + ":", namePart, candidateDefs.get(match.get()));
                    }
                }

                if(defRef != null) {
                    // ensure simple ref (strip prefix)
                    String simple = toSimpleDefinitionRef(defRef);

                    // If definition missing in swagger definitions, try to create it using data object builder
                    if(!swagger.getDefinitions().containsKey(simple) && resolvedNode != null && dataRepo instanceof DataObjectBuilder) {
                        try {
                            DataObjectBuilder builder = (DataObjectBuilder) dataRepo;
                            String name = null;
                            try {
                                name = getNameUnchecked(resolvedNode);
                            } catch (Exception e) {
                                // ignore
                            }
                            if(name != null) {
                                try {
                                    addModelUnchecked(builder, resolvedNode, name);
                                } catch (ClassCastException cce) {
                                    try {
                                        addModelUnchecked(builder, resolvedNode);
                                    } catch (Exception ex) {
                                        log.warn("Cannot create definition for mapping {}: {}", target, ex.toString());
                                    }
                                } catch (Exception ex) {
                                    log.warn("Cannot create definition for mapping {}: {}", target, ex.toString());
                                }
                            } else {
                                try {
                                    addModelUnchecked(builder, resolvedNode);
                                } catch (Exception ex) {
                                    log.warn("Cannot create definition for mapping {}: {}", target, ex.toString());
                                }
                            }

                            // Additionally, ensure nested container/list models inside the resolved node are created
                            try {
                                if(resolvedNode instanceof GroupingDefinition) {
                                    createModelsForGrouping((GroupingDefinition) resolvedNode, builder);
                                } else if(resolvedNode instanceof DataNodeContainer) {
                                    createModelsForContainer((DataNodeContainer) resolvedNode, builder);
                                }
                            } catch (Exception ex) {
                                log.debug("Creating nested definitions for mapping {} failed: {}", target, ex.toString());
                            }

                        } catch (Exception e) {
                            log.warn("Creating definition for mapping {} failed: {}", target, e.toString());
                        }
                    }

                    refModels.add(new RefModel("#/definitions/" + simple));
                } else {
                    throw new IllegalArgumentException(target + " does not exist, check Your configuration & spelling");
                }
            }

            if(refModels.isEmpty()) continue;

            // attach to each node's generated definition (resolve via DataObjectRepo)
            for(DataNodeContainer node : entry.getValue()) {
                String defRef;
                try {
                    defRef = resolveDefinition(node);
                } catch (Exception e) {
                    log.warn("Cannot resolve definition ref for node {}: {}", getNodeId(node), e.toString());
                    continue;
                }
                if(defRef == null || defRef.isEmpty()) {
                    log.warn("No swagger definition found for node {} to attach mount refs", getNodeId(node));
                    continue;
                }
                // get original model if present
                String simpleRef = toSimpleDefinitionRef(defRef);
                Model original = swagger.getDefinitions().get(simpleRef);

                ComposedModel cm = new ComposedModel();
                cm.setInterfaces(refModels);

                // set parent interface if available
                if(!refModels.isEmpty()) cm.parent(refModels.get(0));

                if(original instanceof ModelImpl) {
                    cm.child(copyModelImpl((ModelImpl) original));
                } else if (original instanceof ComposedModel) {
                    // preserve original allOf entries where possible to avoid dropping existing components
                    ComposedModel origCm = (ComposedModel) original;
                    List<Model> origAll = origCm.getAllOf();
                    if(origAll != null && !origAll.isEmpty()) {
                        List<Model> targetAll = cm.getAllOf();
                        if(targetAll == null) {
                            targetAll = new ArrayList<>();
                            cm.setAllOf(targetAll);
                        }
                        // copy items and avoid exact duplicates (by string representation)
                        Set<String> seen = new HashSet<>();
                        for(Model o : targetAll) if(o != null) seen.add(o.toString());
                        for(Model o : origAll) {
                            if(o == null) continue;
                            if(!seen.contains(o.toString())) {
                                targetAll.add(o);
                                seen.add(o.toString());
                            }
                        }
                    }
                } else {
                    cm.parent(new RefModel(defRef));
                }

                // Prune inline empty 'object' entries from allOf to avoid redundant object entries in composed models
                try {
                    List<Model> allOf = cm.getAllOf();
                    if(allOf != null) {
                        allOf.removeIf(m -> {
                            if(m == null) return true;
                            if(m instanceof ModelImpl) {
                                ModelImpl mm = (ModelImpl) m;
                                String t = mm.getType();
                                java.util.Map<String, ?> props = mm.getProperties();
                                boolean isEmptyObject = "object".equals(t) && (props == null || props.isEmpty());
                                return isEmptyObject;
                            }
                            return false;
                        });
                    }
                } catch (Exception e) {
                    log.debug("Error while pruning composed model allOf entries: {}", e.toString());
                }

                // put back using simple name key
                swagger.getDefinitions().put(simpleRef, cm);
                log.info("Attached mount refs to definition {} for mount label {}", simpleRef, label);
            }
        }
    }

    private Map<String, String> buildCandidateDefinitions(Swagger swagger) {
        Map<String, String> candidateDefs = new HashMap<>();
        if(swagger.getDefinitions() == null) return candidateDefs;
        for(String s : swagger.getDefinitions().keySet()) {
            candidateDefs.put(s.toLowerCase(), s);
        }
        return candidateDefs;
    }

    private void addUniqueRefModels(List<RefModel> target, List<RefModel> source) {
        for(RefModel candidate : source) {
            boolean exists = target.stream().anyMatch(r -> r.getSimpleRef().equals(candidate.getSimpleRef()));
            if(!exists) {
                target.add(candidate);
            }
        }
    }

    private String toSimpleDefinitionRef(String defRef) {
        return defRef != null && defRef.startsWith("#/definitions/")
                ? defRef.substring("#/definitions/".length())
                : defRef;
    }

    private RefModel toDefinitionRefModel(String defRef) {
        return new RefModel("#/definitions/" + toSimpleDefinitionRef(defRef));
    }

    private ModelImpl copyModelImpl(ModelImpl source) {
        ModelImpl copy = new ModelImpl();
        copy.setType(source.getType());
        copy.setProperties(source.getProperties());
        copy.setDescription(source.getDescription());
        return copy;
    }

    /**
     * Try to discover mount-point extension argument for a node (DataSchemaNode or GroupingDefinition etc.)
     * using reflection on effective statement.
     * <p>
     * Delegates to {@link EffectiveStatementReflectionHelper} which encapsulates all
     * reflection-based probing so it can be unit-tested in isolation.
     */
    private String findMountPointLabel(Object node) {
        return reflectionHelper.findMountPointLabel(node);
    }

    private String getNodeId(DataNodeContainer node) {
        if(node instanceof org.opendaylight.yangtools.yang.model.api.SchemaNode) {
            try {
                return ((org.opendaylight.yangtools.yang.model.api.SchemaNode)node).getQName().getLocalName();
            } catch (Exception e) { /* ignore */ }
        }
        if(node instanceof org.opendaylight.yangtools.yang.model.api.Module) {
            return ((org.opendaylight.yangtools.yang.model.api.Module)node).getName();
        }
        return node.toString();
    }

    @SuppressWarnings("unchecked")
    private <T extends org.opendaylight.yangtools.yang.model.api.SchemaNode & org.opendaylight.yangtools.yang.model.api.DataNodeContainer> String resolveDefinition(DataNodeContainer node) {
        // DataObjectRepo#getDefinitionRef expects a type that is both SchemaNode and DataNodeContainer.
        if(node == null) return null;
        if(!(node instanceof org.opendaylight.yangtools.yang.model.api.SchemaNode)) return null;
        try {
            // unchecked cast to intersection generic expected by DataObjectRepo
            return dataRepo.getDefinitionRef((T) node);
        } catch (ClassCastException e) {
            // if the node isn't the exact expected shape, return null
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends org.opendaylight.yangtools.yang.model.api.SchemaNode & org.opendaylight.yangtools.yang.model.api.DataNodeContainer>
    String getNameUnchecked(Object o) {
        return dataRepo.getName((T) o);
    }

    @SuppressWarnings("unchecked")
    private <T extends org.opendaylight.yangtools.yang.model.api.SchemaNode & org.opendaylight.yangtools.yang.model.api.DataNodeContainer>
    void addModelUnchecked(DataObjectBuilder builder, Object o, String name) {
        builder.addModel((T) o, name);
    }

    @SuppressWarnings("unchecked")
    private <T extends org.opendaylight.yangtools.yang.model.api.SchemaNode & org.opendaylight.yangtools.yang.model.api.DataNodeContainer>
    void addModelUnchecked(DataObjectBuilder builder, Object o) {
        builder.addModel((T) o);
    }

    // New helper: find module by name in context
    private Optional<Module> findModuleByName(String moduleName) {
        if(moduleName == null || moduleName.isEmpty()) return Optional.empty();
        // ensure the Optional type matches Module (ctx.getModules() may return ? extends Module)
        return ctx.getModules().stream().map(m -> (Module) m).filter(m -> moduleName.equals(m.getName())).findFirst();
    }

    // New helper: resolve all groupings and top-level containers/lists from a module into RefModel list
     private List<RefModel> resolveModuleMappings(String moduleName, Swagger swagger) {
        List<RefModel> refs = new ArrayList<>();
        Optional<Module> modOpt = findModuleByName(moduleName);
        if(!modOpt.isPresent()) return refs;
        Module mod = modOpt.get();

        Set<String> seen = new HashSet<>();

        // 1) groupings in context that belong to this module
        for(GroupingDefinition g : ctx.getGroupings()) {
            String gModule = moduleUtils.toModuleName(g);
            if(!moduleName.equals(gModule)) continue;
            try {
                String defRef = dataRepo.getDefinitionRef(g);
                if(defRef != null) {
                    String simple = toSimpleDefinitionRef(defRef);
                    // ensure model exists in swagger definitions; create if missing
                    if((swagger.getDefinitions() == null || !swagger.getDefinitions().containsKey(simple)) && dataRepo instanceof DataObjectBuilder) {
                        try {
                            DataObjectBuilder builder = (DataObjectBuilder) dataRepo;
                            try {
                                String gname = null;
                                try { gname = getNameUnchecked(g); } catch (Exception e) { /* ignore */ }
                                if(gname != null) addModelUnchecked(builder, g, gname);
                                else addModelUnchecked(builder, g);
                            } catch (Exception ex) { /* ignore */ }
                            try { createModelsForGrouping(g, builder); } catch (Exception ex2) { /* ignore */ }
                        } catch (Exception exx) { /* ignore */ }
                    }
                    if(seen.add(simple)) refs.add(toDefinitionRefModel(simple));
                }
            } catch (Exception e) {
                // try to create using builder if available
                if(dataRepo instanceof DataObjectBuilder) {
                    try {
                        DataObjectBuilder builder = (DataObjectBuilder) dataRepo;
                        try { addModelUnchecked(builder, g); } catch (Exception ex) { /* ignore */ }
                        try { String defRef = dataRepo.getDefinitionRef(g); if(defRef != null) { String simple = toSimpleDefinitionRef(defRef); if(seen.add(simple)) refs.add(toDefinitionRefModel(simple)); } } catch (Exception ex2) { /* ignore */ }

                        // ensure nested container/list models inside grouping are created as well
                        try { createModelsForGrouping(g, builder); } catch (Exception ex3) { /* ignore */ }
                    } catch (Exception ex) { /* ignore */ }
                }
            }
        }

        // 2) include top-level containers and lists from the module as well (to bring nested types)
        if(dataRepo instanceof DataObjectBuilder) {
            DataObjectBuilder builder = (DataObjectBuilder) dataRepo;
            for(org.opendaylight.yangtools.yang.model.api.DataSchemaNode child : mod.getChildNodes()) {
                if(child instanceof ContainerSchemaNode || child instanceof ListSchemaNode) {
                    try {
                        // Try to ensure model exists but DO NOT add its RefModel to the returned refs list.
                        String defRef = resolveDefinition((DataNodeContainer) child);
                        if(defRef == null) {
                            // try to create model
                            try { addModelUnchecked(builder, child); } catch (Exception ex) { /* ignore */ }
                            // after creating, attempt to resolve again (but we won't add to refs)
                            try { String defRef2 = resolveDefinition((DataNodeContainer) child); if(defRef2 != null) { String simple = toSimpleDefinitionRef(defRef2); /* ensure uniqueness in swagger but do not add to refs */ } } catch (Exception ex2) { /* ignore */ }

                            // recursively create nested models for children so nested types exist in definitions
                            try { createModelsForContainer((DataNodeContainer) child, builder); } catch (Exception ex3) { /* ignore */ }
                        } else {
                            // definition already present; still ensure nested definitions exist
                            try { createModelsForContainer((DataNodeContainer) child, builder); } catch (Exception ex3) { /* ignore */ }
                        }
                    } catch (Exception e) {
                        // ignore individual child
                    }
                }
            }
        }

        // Note: For module-only mappings we intentionally include groupings and top-level containers/lists from the module
        // to provide nested definitions required by mounted models.

        return refs;
    }

    // recursively create models for grouping's inner containers/lists
    private void createModelsForGrouping(GroupingDefinition grouping, DataObjectBuilder builder) {
        if(grouping == null || builder == null) return;
        // GroupingDefinition may contain DataSchemaNode children inside its body
        DataNodeHelper.stream(grouping)
                .filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .map(n -> (DataNodeContainer) n)
                .forEach(c -> {
                    try {
                        addModelUnchecked(builder, c);
                    } catch (Exception e) {
                        // ignore
                    }
                    try { createModelsForContainer(c, builder); } catch (Exception e) { /* ignore */ }
                });
    }

    // recursively create models for container/list and its nested containers/lists
    private void createModelsForContainer(DataNodeContainer container, DataObjectBuilder builder) {
        if(container == null || builder == null) return;
        // for each child that is a container or list, ensure model exists and recurse
        for(Object childObj : ((org.opendaylight.yangtools.yang.model.api.DataNodeContainer)container).getChildNodes()) {
            if(!(childObj instanceof org.opendaylight.yangtools.yang.model.api.DataSchemaNode)) continue;
            org.opendaylight.yangtools.yang.model.api.DataSchemaNode child = (org.opendaylight.yangtools.yang.model.api.DataSchemaNode) childObj;
            if(child instanceof ContainerSchemaNode || child instanceof ListSchemaNode) {
                DataNodeContainer dc = (DataNodeContainer) child;
                try {
                    addModelUnchecked(builder, dc);
                } catch (Exception e) {
                    // ignore
                }
                // recurse
                try { createModelsForContainer(dc, builder); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    // attach RPCs from module as operations under each mount node
    private void attachModuleRpcsToMount(Module module, List<DataNodeContainer> mountNodes, Swagger swagger) {
        if(module == null || mountNodes == null || mountNodes.isEmpty()) return;
        log.debug("attachModuleRpcsToMount invoked for module {} with {} mount nodes", module.getName(), mountNodes.size());
        if(!(dataRepo instanceof DataObjectBuilder)) {
            log.info("No DataObjectBuilder available - skipping attaching RPC models for module {}", module.getName());
            return;
        }
        DataObjectBuilder builder = (DataObjectBuilder) dataRepo;

        for(RpcDefinition rpc : module.getRpcs()) {
            try {
                log.debug("Processing RPC {} in module {}", rpc.getQName().getLocalName(), module.getName());
                InputSchemaNode input = rpc.getInput();
                OutputSchemaNode output = rpc.getOutput();
                input = input.getChildNodes().isEmpty() ? null : input;
                output = output.getChildNodes().isEmpty() ? null : output;

                // create base operation (for global /operations paths) tagged with RPC module
                Operation baseOp = new Operation();
                baseOp.response(400, new Response().description("Internal error"));
                baseOp.setParameters(new ArrayList<>());
                baseOp.tag(module.getName());

                if(input != null) {
                    builder.addModel(input);
                    ModelImpl inputModel = new ModelImpl().type(ModelImpl.OBJECT);
                    inputModel.addProperty("input", new RefProperty(builder.getDefinitionRef(input)));
                    baseOp.summary("operates on " + builder.getName(ContainerSchemaNodes.forRPC(rpc)));
                    baseOp.description("operates on " + builder.getName(ContainerSchemaNodes.forRPC(rpc)));
                    baseOp.parameter(new BodyParameter()
                            .name(builder.getName(input) + ".body-param")
                            .schema(inputModel)
                            .description(input.getDescription().orElse(null))
                    );
                }

                if(output != null) {
                    ModelImpl model = new ModelImpl().type(ModelImpl.OBJECT);
                    model.addProperty("output", new RefProperty(builder.getDefinitionRef(output)));
                    builder.addModel(output);
                    baseOp.response(200, new Response()
                            .responseSchema(model)
                            .description(output.getDescription().orElse("Correct response")));
                }

                baseOp.response(201, new Response().description("No response"));

                // attach to each mount node as a path (operations root)
                for(DataNodeContainer mount : mountNodes) {
                    // Only create mounted RPC under data path; do not create operations-root entries here
                    try {
                        String dataPath = findDataPathForMount(mount, swagger);
                        if(dataPath != null) {
                            Operation mountedOp = copyOperation(baseOp);
                            List<Parameter> inheritedParams = extractPathParameters(dataPath, swagger);
                            if (!inheritedParams.isEmpty()) {
                                List<Parameter> merged = new ArrayList<>(inheritedParams);
                                if (mountedOp.getParameters() != null) merged.addAll(mountedOp.getParameters());
                                mountedOp.setParameters(merged);
                            }
                            String mountModuleName = null;
                            try {
                                if(mount instanceof org.opendaylight.yangtools.yang.model.api.SchemaNode) {
                                    mountModuleName = moduleUtils.toModuleName((org.opendaylight.yangtools.yang.model.api.SchemaNode) mount);
                                } else if(mount instanceof Module) {
                                    mountModuleName = ((Module) mount).getName();
                                }
                            } catch (Exception e) {
                                mountModuleName = module.getName();
                            }

                            mountedOp.tag(module.getName());
                            String mountedRpcKey = dataPath + "/" + module.getName() + ":" + rpc.getQName().getLocalName();
                            if(swagger.getPaths() != null && swagger.getPaths().containsKey(mountedRpcKey)) {
                                log.warn("Mounted RPC path {} already exists in swagger, skipping", mountedRpcKey);
                            } else {
                                if(swagger.getPaths() == null) swagger.setPaths(new java.util.LinkedHashMap<>());
                                swagger.path(mountedRpcKey, new Path().post(mountedOp));
                                log.info("Attached mounted RPC {} under data path {} for mount node {}", rpc.getQName().getLocalName(), mountedRpcKey, getNodeId(mount));

                                // keep global operations/* entries (generated by the main path handlers);
                                // this postprocessor only adds mounted data-path operations and must not remove globals
                             }
                         } else {
                             log.debug("Could not find data path for mount node {}, skipping mounted RPC creation for {}", getNodeId(mount), rpc.getQName().getLocalName());
                         }
                     } catch (Exception e) {
                         log.warn("Failed to attach mounted RPC {} for mount node {}: {}", rpc.getQName().getLocalName(), getNodeId(mount), e.toString());
                     }
                }

            } catch (Exception e) {
                log.warn("Failed to attach RPC {} from module {}: {}", rpc.getQName().getLocalName(), module.getName(), e.toString());
            }
        }
    }

    // Generate mounted data API for module under each mount data path.
    private void attachModuleDataPathsToMount(Module module, List<DataNodeContainer> mountNodes, Swagger swagger) {
        if(module == null || mountNodes == null || mountNodes.isEmpty() || swagger == null) return;

        Map<String, Path> sourceDataPaths = resolveModuleDataPaths(module, swagger);
        if(sourceDataPaths.isEmpty()) {
            log.debug("No source data paths found for module {}, skipping mounted data path generation", module.getName());
            return;
        }

        for(DataNodeContainer mount : mountNodes) {
            String mountDataPath = findDataPathForMount(mount, swagger);
            if(mountDataPath == null) {
                log.debug("Could not find data path for mount node {}, skipping mounted data paths for module {}", getNodeId(mount), module.getName());
                continue;
            }

            for(Map.Entry<String, Path> source : sourceDataPaths.entrySet()) {
                String mountedPath = mountDataPath + source.getKey().substring("/data".length());
                if(swagger.getPaths() != null && swagger.getPaths().containsKey(mountedPath)) continue;

                if(swagger.getPaths() == null) swagger.setPaths(new LinkedHashMap<>());
                Path copiedPath = copyPathWithModuleTag(source.getValue(), module.getName());
                List<Parameter> inheritedParams = extractPathParameters(mountDataPath, swagger);
                if (!inheritedParams.isEmpty()) {
                    injectPathParameters(copiedPath, inheritedParams);
                }
                swagger.path(mountedPath, copiedPath);
                log.info("Attached mounted data path {} for module {} under mount node {}", mountedPath, module.getName(), getNodeId(mount));
            }
        }
    }

    private Map<String, Path> resolveModuleDataPaths(Module module, Swagger swagger) {
        Map<String, Path> sourceDataPaths = new LinkedHashMap<>();
        String modulePrefix = "/data/" + module.getName() + ":";

        if(swagger.getPaths() != null) {
            for(Map.Entry<String, Path> entry : swagger.getPaths().entrySet()) {
                String pathKey = entry.getKey();
                if(pathKey != null && pathKey.startsWith(modulePrefix) && entry.getValue() != null) {
                    sourceDataPaths.put(pathKey, entry.getValue());
                }
            }
        }

        if(!sourceDataPaths.isEmpty()) return sourceDataPaths;

        // Module was not part of modulesToGenerate - generate its data API in isolation and reuse it as source.
        Swagger moduleSwagger = moduleSwaggerCache.computeIfAbsent(module.getName(), ignored -> generateModuleDataSwagger(module));
        if(moduleSwagger == null || moduleSwagger.getPaths() == null) return sourceDataPaths;

        for(Map.Entry<String, Path> entry : moduleSwagger.getPaths().entrySet()) {
            String pathKey = entry.getKey();
            if(pathKey != null && pathKey.startsWith(modulePrefix) && entry.getValue() != null) {
                sourceDataPaths.put(pathKey, entry.getValue());
            }
        }

        if(moduleSwagger.getDefinitions() != null) {
            if(swagger.getDefinitions() == null) swagger.setDefinitions(new LinkedHashMap<>());
            moduleSwagger.getDefinitions().forEach((name, model) -> swagger.getDefinitions().putIfAbsent(name, model));
        }

        return sourceDataPaths;
    }

    private Swagger generateModuleDataSwagger(Module module) {
        try {
            SwaggerGenerator generator = new SwaggerGenerator(ctx, Collections.singletonList(module)).defaultConfig()
                    .elements(SwaggerGenerator.Elements.DATA)
                    .pathHandler(new com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder().useModuleName());
            return generator.generate();
        } catch (Exception e) {
            log.warn("Failed to generate standalone data API for module {}: {}", module.getName(), e.toString());
            return null;
        }
    }

    // New helper: try to discover the data path key in swagger for a given mount node
    private String findDataPathForMount(DataNodeContainer mount, Swagger swagger) {
        if(mount == null || swagger == null || swagger.getPaths() == null) return null;
        String nodeId = getNodeId(mount);
        if(nodeId == null) return null;

        // candidate keys that start with /data/ and contain the node id as a segment
        List<String> candidates = new ArrayList<>();
        List<String> terminalCandidates = new ArrayList<>();
        for(String key : swagger.getPaths().keySet()) {
            if(!key.startsWith("/data/")) continue;
            // split into segments, ignore leading empty
            String[] segs = key.split("/");
            for(int i = 0; i < segs.length; i++) {
                String s = segs[i];
                if(s == null || s.isEmpty()) continue;
                // compare with nodeId or with module:nodeId form
                if(s.equals(nodeId) || s.endsWith(":" + nodeId) || s.startsWith(nodeId + "=") || s.contains(":" + nodeId + "=") || s.contains(":" + nodeId)) {
                    candidates.add(key);
                    if(i == segs.length - 1) {
                        terminalCandidates.add(key);
                    }
                    break;
                }
            }
        }

        if(candidates.isEmpty()) return null;
        if(!terminalCandidates.isEmpty()) {
            terminalCandidates.sort(Comparator.comparingInt(String::length).reversed());
            return terminalCandidates.get(0);
        }
        // prefer the longest (most specific) path
        candidates.sort(Comparator.comparingInt(String::length).reversed());
        return candidates.get(0);
    }

    private Path copyPathWithModuleTag(Path src, String moduleName) {
        Path dst = new Path();
        if(src == null) return dst;

        if(src.getGet() != null) dst.setGet(copyOperationWithTag(src.getGet(), moduleName));
        if(src.getPut() != null) dst.setPut(copyOperationWithTag(src.getPut(), moduleName));
        if(src.getPost() != null) dst.setPost(copyOperationWithTag(src.getPost(), moduleName));
        if(src.getDelete() != null) dst.setDelete(copyOperationWithTag(src.getDelete(), moduleName));
        if(src.getPatch() != null) dst.setPatch(copyOperationWithTag(src.getPatch(), moduleName));
        if(src.getHead() != null) dst.setHead(copyOperationWithTag(src.getHead(), moduleName));
        if(src.getOptions() != null) dst.setOptions(copyOperationWithTag(src.getOptions(), moduleName));

        try {
            if(src.getParameters() != null) dst.setParameters(new ArrayList<>(src.getParameters()));
        } catch (Exception e) {
            // ignore
        }

        return dst;
    }

    private Operation copyOperationWithTag(Operation src, String moduleName) {
        Operation dst = copyOperation(src);
        dst.setTags(new ArrayList<>(Collections.singletonList(moduleName)));
        return dst;
    }

    // New helper: shallow copy of an Operation (parameters, responses, summary, description) without tags
    private Operation copyOperation(Operation src) {
        Operation dst = new Operation();
        try {
            dst.setParameters(src.getParameters() == null ? null : new ArrayList<>(src.getParameters()));
        } catch (Exception e) {
            // ignore
        }
        try {
            if(src.getResponses() != null) {
                Map<String, Response> copy = new LinkedHashMap<>();
                copy.putAll(src.getResponses());
                dst.setResponses(copy);
            }
        } catch (Exception e) {
            // ignore
        }
        try { dst.setSummary(src.getSummary()); } catch (Exception e) {}
        try { dst.setDescription(src.getDescription()); } catch (Exception e) {}
        try { dst.setOperationId(src.getOperationId()); } catch (Exception e) {}
        try { dst.setConsumes(src.getConsumes() == null ? null : new ArrayList<>(src.getConsumes())); } catch (Exception e) {}
        try { dst.setProduces(src.getProduces() == null ? null : new ArrayList<>(src.getProduces())); } catch (Exception e) {}
        try { dst.setSchemes(src.getSchemes() == null ? null : new ArrayList<>(src.getSchemes())); } catch (Exception e) {}
        try { dst.setDeprecated(src.isDeprecated()); } catch (Exception e) {}
        try { dst.setSecurity(src.getSecurity() == null ? null : new ArrayList<>(src.getSecurity())); } catch (Exception e) {}
        try { dst.setExternalDocs(src.getExternalDocs()); } catch (Exception e) {}
        // do not copy tags - caller should set appropriate tag
        return dst;
    }

    /**
     * Extract path parameters (e.g. {@code {name}}) declared on {@code path} by looking them up
     * in the existing swagger operation definitions.  Falls back to a synthetic string parameter
     * when a declaration cannot be found.
     */
    private List<Parameter> extractPathParameters(String path, Swagger swagger) {
        if (path == null || path.isEmpty() || swagger == null || swagger.getPaths() == null) {
            return Collections.emptyList();
        }
        List<String> paramNames = new ArrayList<>();
        int start = path.indexOf('{');
        while (start >= 0) {
            int end = path.indexOf('}', start);
            if (end < 0) break;
            paramNames.add(path.substring(start + 1, end));
            start = path.indexOf('{', end);
        }
        if (paramNames.isEmpty()) return Collections.emptyList();

        List<Parameter> result = new ArrayList<>();
        Set<String> resolved = new HashSet<>();

        for (String paramName : paramNames) {
            boolean found = false;
            for (Map.Entry<String, Path> entry : swagger.getPaths().entrySet()) {
                if (!entry.getKey().contains("{" + paramName + "}")) continue;
                for (Operation op : collectOperations(entry.getValue())) {
                    if (op == null || op.getParameters() == null) continue;
                    for (Parameter p : op.getParameters()) {
                        if ("path".equals(p.getIn()) && paramName.equals(p.getName()) && resolved.add(paramName)) {
                            result.add(p);
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
                if (found) break;
            }
            if (!found && resolved.add(paramName)) {
                PathParameter pp = new PathParameter();
                pp.setName(paramName);
                pp.setRequired(true);
                pp.setType("string");
                result.add(pp);
            }
        }
        return result;
    }

    private List<Operation> collectOperations(Path path) {
        List<Operation> ops = new ArrayList<>();
        if (path.getGet() != null) ops.add(path.getGet());
        if (path.getPut() != null) ops.add(path.getPut());
        if (path.getPost() != null) ops.add(path.getPost());
        if (path.getDelete() != null) ops.add(path.getDelete());
        if (path.getPatch() != null) ops.add(path.getPatch());
        return ops;
    }

    /** Prepend {@code params} to every operation on {@code path}, skipping any already declared. */
    private void injectPathParameters(Path path, List<Parameter> params) {
        if (params.isEmpty()) return;
        for (Operation op : collectOperations(path)) {
            if (op == null) continue;
            List<Parameter> existing = op.getParameters() == null ? new ArrayList<>() : new ArrayList<>(op.getParameters());
            Set<String> existingPathParamNames = new HashSet<>();
            for (Parameter p : existing) {
                if ("path".equals(p.getIn())) existingPathParamNames.add(p.getName());
            }
            List<Parameter> merged = new ArrayList<>();
            for (Parameter p : params) {
                if (!existingPathParamNames.contains(p.getName())) merged.add(p);
            }
            merged.addAll(existing);
            op.setParameters(merged);
        }
    }
}
