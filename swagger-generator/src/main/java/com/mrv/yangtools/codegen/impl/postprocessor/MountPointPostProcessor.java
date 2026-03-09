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

    private static volatile boolean declaredAccessRestricted = false;

    private final Map<String, List<String>> mappings;
    private final EffectiveModelContext ctx;
    private final ModuleUtils moduleUtils;
    private final DataObjectRepo dataRepo;

    public MountPointPostProcessor(Map<String, List<String>> mappings, EffectiveModelContext ctx, ModuleUtils moduleUtils, DataObjectRepo dataRepo) {
        this.mappings = mappings == null ? Collections.emptyMap() : mappings;
        this.ctx = ctx;
        this.moduleUtils = moduleUtils;
        this.dataRepo = dataRepo;
    }

    // Collect mount-point labels across all modules and return mapping label -> list of DataNodeContainer nodes
    private Map<String, List<DataNodeContainer>> getMointPointFromModules() {
        Map<String, List<DataNodeContainer>> nodesByLabel = new HashMap<>();
        ctx.getModules()
                .forEach(module -> collectMountPointsFromContainer(module, nodesByLabel));
        return nodesByLabel;
    }

    // Scan provided container (module/container/list) for DataSchemaNode instances and try to find mount-point label on each
    private void collectMountPointsFromContainer(DataNodeContainer container, Map<String, List<DataNodeContainer>> nodesByLabel) {
        // For debugging keep basic info
        log.debug("Scanning container for mount-points: {}", container);

        // DataNodeHelper.stream(container) yields schema nodes; check each for mount-point extension
        DataNodeHelper.stream(container)
                .filter(n -> n instanceof DataSchemaNode)
                .map(n -> (DataSchemaNode) n)
                .forEach(node -> {
                    String label = findMountPointLabel(node);
                    if (label != null && !label.isEmpty()) {
                        log.info("Found mount-point label '{}' in node {}", label, node);
                        // ensure we store the container node (only Container/List are DataNodeContainer)
                        if (node instanceof DataNodeContainer) {
                            nodesByLabel.computeIfAbsent(label, k -> new ArrayList<>()).add((DataNodeContainer) node);
                        } else {
                            // in some models the mount-point may appear on grouping or other statement; try to attach parent container
                            // find parent container by walking up via module scan (best-effort)
                            // fallback: add the module root as the container
                            // note: Module also implements DataNodeContainer, so add module if no better parent
                            nodesByLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(container);
                        }
                    }
                });

        // Additionally, check groupings inside the container (groupings are not DataSchemaNode) using DataNodeHelper
        DataNodeHelper.stream(container)
                .filter(n -> n instanceof GroupingDefinition)
                .map(n -> (GroupingDefinition) n)
                .forEach(g -> {
                    String label = findMountPointLabel(g);
                    if (label != null && !label.isEmpty()) {
                        log.info("Found mount-point label '{}' in grouping {}", label, g);
                        // groupings are not DataNodeContainer, associate to the module-level container
                        nodesByLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(container);
                    }
                });
    }

    @Override
    public void accept(Swagger swagger) {
        if(swagger.getDefinitions() == null || swagger.getDefinitions().isEmpty()) return;

        // build a set of candidate refs based on provided module:grouping strings
        Map<String, String> candidateDefs = new HashMap<>();
        if(swagger.getDefinitions() != null) {
            for(String s : swagger.getDefinitions().keySet()) {
                candidateDefs.put(s.toLowerCase(), s);
            }
        }

        // collect nodes by mount-point label
        Map<String, List<DataNodeContainer>> nodesByLabel = getMointPointFromModules();

        if(nodesByLabel.isEmpty()) {
            log.info("No mount-point extensions found in model");
            return;
        }

        // for each found mount label, look up CLI mapping and attach definitions to corresponding swagger models
        for(Map.Entry<String, List<DataNodeContainer>> entry : nodesByLabel.entrySet()) {
            String label = entry.getKey();
            List<String> mapped = mappings.get(label);
            if(mapped == null || mapped.isEmpty()) continue;

            // resolve mapped module:grouping entries to definition refs using context and dataRepo
            List<RefModel> refModels = new ArrayList<>();
            for(String map : mapped) {
                // New behavior: if mapping is module-only (no ':') and module exists in context, treat as module mapping
                String trimmed = map == null ? "" : map.trim();
                if(!trimmed.contains(":")) {
                    Optional<Module> mod = findModuleByName(trimmed);
                    if(mod.isPresent()) {
                        // gather refs from the whole module
                        List<RefModel> moduleRefs = resolveModuleMappings(mod.get().getName(), swagger);
                        // add unique
                        for(RefModel rm : moduleRefs) {
                            boolean exists = refModels.stream().anyMatch(r -> r.getSimpleRef().equals(rm.getSimpleRef()));
                            if(!exists) refModels.add(rm);
                        }

                        // attach RPCs from this module to the mount nodes
                        attachModuleRpcsToMount(mod.get(), entry.getValue(), swagger);
                        continue; // mapping entry handled
                    } else {
                        // module-only mapping but module not found -> throw
                        throw new IllegalArgumentException(trimmed + " does not exist, check Your configuration & spelling");
                    }
                }

                String[] parts = map.split(":",2);
                String modulePart = parts.length > 0 ? parts[0].trim() : "";
                String namePart = parts.length > 1 ? parts[1].trim() : parts[0].trim();

                String defRef = null;
                Object resolvedNode = null;
                // 1) try groupings
                for(GroupingDefinition g : ctx.getGroupings()) {
                    String gLocal = g.getQName().getLocalName();
                    String gModule = moduleUtils.toModuleName(g);
                    if(gLocal.equals(namePart) && (modulePart.isEmpty() || gModule.equals(modulePart))) {
                        try { defRef = dataRepo.getDefinitionRef(g); resolvedNode = g; } catch (Exception e) { defRef = null; resolvedNode = null; }
                        if(defRef != null) break;
                    }
                }
                // 2) try containers/lists
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
                // 3) fallback to previous candidateDefs heuristic
                if(defRef == null && !candidateDefs.isEmpty()) {
                    String lower = namePart.toLowerCase();
                    Optional<String> match = candidateDefs.keySet().stream().filter(k -> k.contains(lower) && (modulePart.isEmpty() || k.contains(modulePart.toLowerCase()))).findFirst();
                    if(match.isPresent()) defRef = "#/definitions/" + candidateDefs.get(match.get());
                }

                if(defRef != null) {
                    // ensure simple ref (strip prefix)
                    String simple = defRef.startsWith("#/definitions/") ? defRef.substring("#/definitions/".length()) : defRef;

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
                                        log.warn("Cannot create definition for mapping {}: {}", map, ex.toString());
                                    }
                                } catch (Exception ex) {
                                    log.warn("Cannot create definition for mapping {}: {}", map, ex.toString());
                                }
                            } else {
                                try {
                                    addModelUnchecked(builder, resolvedNode);
                                } catch (Exception ex) {
                                    log.warn("Cannot create definition for mapping {}: {}", map, ex.toString());
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
                                log.debug("Creating nested definitions for mapping {} failed: {}", map, ex.toString());
                            }

                        } catch (Exception e) {
                            log.warn("Creating definition for mapping {} failed: {}", map, e.toString());
                        }
                    }

                    refModels.add(new RefModel("#/definitions/" + simple));
                } else {
                    // if we reached this point, the mapping string could not be resolved -> throw an exception
                    throw new IllegalArgumentException(map + " does not exist, check Your configuration & spelling");
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
                String simpleRef = defRef.startsWith("#/definitions/") ? defRef.substring("#/definitions/".length()) : defRef;
                Model original = swagger.getDefinitions().get(simpleRef);

                // remember whether the original already contained an empty inline 'object' entry
                boolean hadEmptyObjectBefore = false;
                if(original instanceof ComposedModel) {
                    List<Model> origAllOf = ((ComposedModel) original).getAllOf();
                    if(origAllOf != null) {
                        for(Model o : origAllOf) {
                            if(o instanceof ModelImpl) {
                                ModelImpl mm = (ModelImpl) o;
                                String t = mm.getType();
                                java.util.Map<String, ?> props = mm.getProperties();
                                if("object".equals(t) && (props == null || props.isEmpty())) {
                                    hadEmptyObjectBefore = true;
                                    break;
                                }
                            }
                        }
                    }
                } else if(original instanceof ModelImpl) {
                    ModelImpl mm = (ModelImpl) original;
                    String t = mm.getType();
                    java.util.Map<String, ?> props = mm.getProperties();
                    if("object".equals(t) && (props == null || props.isEmpty())) {
                        hadEmptyObjectBefore = true;
                    }
                }

                ComposedModel cm = new ComposedModel();
                cm.setInterfaces(refModels);

                // set parent interface if available
                if(!refModels.isEmpty()) cm.parent(refModels.get(0));

                if(original instanceof ModelImpl) {
                    ModelImpl mi = (ModelImpl) original;
                    ModelImpl copy = new ModelImpl();
                    copy.setType(mi.getType());
                    copy.setProperties(mi.getProperties());
                    copy.setDescription(mi.getDescription());
                    cm.child(copy);
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

    /**
     * Try to discover mount-point extension argument for a node (DataSchemaNode or GroupingDefinition etc.) using reflection on effective statement
     */
    private String findMountPointLabel(Object node) {
        if(node == null) return null;
        try {
            // try to call asEffectiveStatement() or getEffectiveStatement() reflectively (public or non-public)
            Object eff = null;
            // helper to try a named method (public) and then invoke it safely
            java.util.function.BiFunction<Object, String, Object> tryInvokePublic = (obj, methodName) -> {
                try {
                    java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
                    try {
                        return m.invoke(obj);
                    } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException | RuntimeException iae) {
                        log.trace("Cannot invoke public method {} on {}: {}", methodName, obj.getClass(), iae.toString());
                        return null;
                    }
                } catch (NoSuchMethodException ns) {
                    return null;
                }
            };

            eff = tryInvokePublic.apply(node, "asEffectiveStatement");
            if(eff == null) eff = tryInvokePublic.apply(node, "getEffectiveStatement");

            if(eff == null && !declaredAccessRestricted) {
                // try declared (non-public) methods as a fallback; setAccessible may be blocked by Java modules
                try {
                    java.lang.reflect.Method dm = null;
                    try {
                        dm = node.getClass().getDeclaredMethod("asEffectiveStatement");
                    } catch (NoSuchMethodException ns) {
                        // ignore
                    }
                    if(dm != null) {
                        try {
                            dm.setAccessible(true);
                            eff = dm.invoke(node);
                        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException | RuntimeException iae) {
                            log.trace("Declared access to asEffectiveStatement blocked for {}: {}", node.getClass(), iae.toString());
                            declaredAccessRestricted = true;
                        }
                    }
                } catch (Exception ex) {
                    // ignore

                }
                if(eff == null && !declaredAccessRestricted) {
                    try {
                        java.lang.reflect.Method dm2 = null;
                        try {
                            dm2 = node.getClass().getDeclaredMethod("getEffectiveStatement");
                        } catch (NoSuchMethodException ns2) {
                            // ignore
                        }
                        if(dm2 != null) {
                            try {
                                dm2.setAccessible(true);
                                eff = dm2.invoke(node);
                            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException | RuntimeException iae) {
                                log.trace("Declared access to getEffectiveStatement blocked for {}: {}", node.getClass(), iae.toString());
                                declaredAccessRestricted = true;
                            }
                        }
                    } catch (Exception ex2) {
                        // ignore
                    }
                }
            }

            if(eff == null) return null;

            // collect potential collection-like holders (public methods, declared methods and declared fields)
            List<Collection<?>> candidateCols = new ArrayList<>();

            // helper to add values from maps or collections
            java.util.function.Consumer<Object> addPossibleCollection = (obj) -> {
                if(obj == null) return;
                if(obj instanceof Collection) {
                    candidateCols.add((Collection<?>) obj);
                } else if(obj instanceof Map) {
                    Map<?,?> m = (Map<?,?>) obj;
                    for(Object v : m.values()) {
                        if(v instanceof Collection) candidateCols.add((Collection<?>) v);
                        else candidateCols.add(Collections.singletonList(v));
                    }
                } else if(obj.getClass().isArray()) {
                    Object[] arr = (Object[]) obj;
                    candidateCols.add(Arrays.asList(arr));
                } else {
                    // single item -> wrap into collection
                    candidateCols.add(Collections.singletonList(obj));
                }
            };

            // public methods
            for(java.lang.reflect.Method method : eff.getClass().getMethods()) {
                try {
                    Class<?> rt = method.getReturnType();
                    if(java.util.Collection.class.isAssignableFrom(rt) || java.util.Map.class.isAssignableFrom(rt) || rt.isArray()) {
                        method.setAccessible(true);
                        Object res = null;
                        try {
                            res = method.invoke(eff);
                        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException iae) {
                            // if access is blocked, mark restriction and skip declared access attempts later
                            log.trace("Public method invocation blocked for {}#{}: {}", eff.getClass(), method.getName(), iae.toString());
                            declaredAccessRestricted = true;
                            continue;
                        }
                        addPossibleCollection.accept(res);
                    }
                } catch (Exception ex) {
                    // ignore this method
                }
            }

            // declared (possibly non-public) methods
            if(!declaredAccessRestricted) {
                for(java.lang.reflect.Method method : eff.getClass().getDeclaredMethods()) {
                    try {
                        Class<?> rt = method.getReturnType();
                        if(java.util.Collection.class.isAssignableFrom(rt) || java.util.Map.class.isAssignableFrom(rt) || rt.isArray()) {
                            method.setAccessible(true);
                            Object res = null;
                            try {
                                res = method.invoke(eff);
                            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException iae) {
                                log.trace("Declared method invocation blocked for {}#{}: {}", eff.getClass(), method.getName(), iae.toString());
                                declaredAccessRestricted = true;
                                break; // stop trying declared methods
                            }
                            addPossibleCollection.accept(res);
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }

            // declared fields (some implementations keep sub-statement in private fields)
            if(!declaredAccessRestricted) {
                for(java.lang.reflect.Field f : eff.getClass().getDeclaredFields()) {
                    try {
                        Class<?> ft = f.getType();
                        if(java.util.Collection.class.isAssignableFrom(ft) || java.util.Map.class.isAssignableFrom(ft) || ft.isArray()) {
                            f.setAccessible(true);
                            Object res = null;
                            try {
                                res = f.get(eff);
                            } catch (IllegalAccessException iae) {
                                log.trace("Declared field access blocked for {}#{}: {}", eff.getClass(), f.getName(), iae.toString());
                                declaredAccessRestricted = true;
                                break;
                            }
                            addPossibleCollection.accept(res);
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }

            // iterate collected sub-statement collections and look for mount-point tokens
            for(Collection<?> col : candidateCols) {
                if(col == null) continue;
                for(Object item : col) {
                    if(item == null) continue;
                    String text = item.toString().toLowerCase();
                    if(text.contains("mount-point") || text.contains("yangmnt:mount-point") || text.contains("yangmnt:mount_point")) {
                        // try to get argument via methods on item (try declared methods too)
                        String arg = tryGetStringProperty(item, new String[]{"getArgument","getArg","getValue","getArgumentValue","getLabel","getName"});
                        if(arg != null && !arg.isEmpty()) return arg;

                        // try declared methods as fallback for non-public item types
                        if(!declaredAccessRestricted) {
                            for(String nm : new String[]{"getArgument","getArg","getValue","getArgumentValue","getLabel","getName"}) {
                                try {
                                    java.lang.reflect.Method dm = item.getClass().getDeclaredMethod(nm);
                                    dm.setAccessible(true);
                                    Object v = dm.invoke(item);
                                    if(v != null) return v.toString();
                                } catch (IllegalAccessException iae) {
                                    log.trace("Declared access blocked for item method {} of {}: {}", nm, item.getClass(), iae.toString());
                                    declaredAccessRestricted = true;
                                    break;
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                        }

                        // fallback: try to parse token after 'mount-point' in toString
                        int idx = text.indexOf("mount-point");
                        if(idx >= 0) {
                            String after = text.substring(idx);
                            // crude parse
                            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("mount[-_]point\\s+([a-zA-Z0-9_-]+)").matcher(after);
                            if(mm.find()) return mm.group(1);
                        }
                    }
                }
            }

        } catch (java.lang.reflect.InaccessibleObjectException iae) {
            // access blocked by JVM/module system: avoid noisy error logs and mark restriction
            log.trace("Reflective access to effective statement blocked for node class {}: {}", node.getClass(), iae.toString());
            declaredAccessRestricted = true;
            return null;
        } catch (Exception e) {
            // ignore and return null
            log.debug("Error while inspecting node for mount-point: {}", e.toString());
        }
        return null;
    }

    private String tryGetStringProperty(Object obj, String[] candidates) {
        for(String name : candidates) {
            try {
                java.lang.reflect.Method m = obj.getClass().getMethod(name);
                Object v = m.invoke(obj);
                if(v != null) return v.toString();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
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
                    String simple = defRef.startsWith("#/definitions/") ? defRef.substring("#/definitions/".length()) : defRef;
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
                    if(seen.add(simple)) refs.add(new RefModel("#/definitions/" + simple));
                }
            } catch (Exception e) {
                // try to create using builder if available
                if(dataRepo instanceof DataObjectBuilder) {
                    try {
                        DataObjectBuilder builder = (DataObjectBuilder) dataRepo;
                        try { addModelUnchecked(builder, g); } catch (Exception ex) { /* ignore */ }
                        try { String defRef = dataRepo.getDefinitionRef(g); if(defRef != null) { String simple = defRef.startsWith("#/definitions/") ? defRef.substring("#/definitions/".length()) : defRef; if(seen.add(simple)) refs.add(new RefModel("#/definitions/" + simple)); } } catch (Exception ex2) { /* ignore */ }

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
                            try { String defRef2 = resolveDefinition((DataNodeContainer) child); if(defRef2 != null) { String simple = defRef2.startsWith("#/definitions/") ? defRef2.substring("#/definitions/".length()) : defRef2; /* ensure uniqueness in swagger but do not add to refs */ } } catch (Exception ex2) { /* ignore */ }

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

    // New helper: recursively create models for grouping's inner containers/lists
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

    // New helper: recursively create models for container/list and its nested containers/lists
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

    // New helper: attach RPCs from module as operations under each mount node
    private void attachModuleRpcsToMount(Module module, List<DataNodeContainer> mountNodes, Swagger swagger) {
        if(module == null || mountNodes == null || mountNodes.isEmpty()) return;
        if(!(dataRepo instanceof DataObjectBuilder)) {
            log.info("No DataObjectBuilder available — skipping attaching RPC models for module {}", module.getName());
            return;
        }
        DataObjectBuilder builder = (DataObjectBuilder) dataRepo;
        String operationsPrefix = "/operations/";

        for(RpcDefinition rpc : module.getRpcs()) {
            try {
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
                    String pathKey = operationsPrefix + getNodeId(mount) + "/" + rpc.getQName().getLocalName();
                    if(swagger.getPaths() != null && swagger.getPaths().containsKey(pathKey)) {
                        log.warn("RPC path {} already exists in swagger, skipping", pathKey);
                    } else {
                        if(swagger.getPaths() == null) swagger.setPaths(new java.util.LinkedHashMap<>());
                        swagger.path(pathKey, new Path().post(baseOp));
                        log.info("Attached RPC {} under path {} for mount node {}", rpc.getQName().getLocalName(), pathKey, getNodeId(mount));
                    }

                    // Additionally try to attach RPC under the data path where the mount node lives (mounted RPC)
                    try {
                        String dataPath = findDataPathForMount(mount, swagger);
                        if(dataPath != null) {
                            // create a copy of operation and tag it with the mounting module name
                            Operation mountedOp = copyOperation(baseOp);
                            String mountModuleName = null;
                            try {
                                if(mount instanceof org.opendaylight.yangtools.yang.model.api.SchemaNode) {
                                    mountModuleName = moduleUtils.toModuleName((org.opendaylight.yangtools.yang.model.api.SchemaNode) mount);
                                } else if(mount instanceof Module) {
                                    mountModuleName = ((Module) mount).getName();
                                }
                            } catch (Exception e) {
                                // fallback to original rpc module
                                mountModuleName = module.getName();
                            }
                            // set tag to module where mounted
                            if(mountModuleName != null) mountedOp.tag(mountModuleName);

                            String mountedRpcKey = dataPath + "/" + (mountModuleName != null ? mountModuleName : module.getName()) + ":" + rpc.getQName().getLocalName();
                            if(swagger.getPaths() != null && swagger.getPaths().containsKey(mountedRpcKey)) {
                                log.warn("Mounted RPC path {} already exists in swagger, skipping", mountedRpcKey);
                            } else {
                                if(swagger.getPaths() == null) swagger.setPaths(new java.util.LinkedHashMap<>());
                                swagger.path(mountedRpcKey, new Path().post(mountedOp));
                                log.info("Attached mounted RPC {} under data path {} for mount node {}", rpc.getQName().getLocalName(), mountedRpcKey, getNodeId(mount));
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

    // New helper: try to discover the data path key in swagger for a given mount node
    private String findDataPathForMount(DataNodeContainer mount, Swagger swagger) {
        if(mount == null || swagger == null || swagger.getPaths() == null) return null;
        String nodeId = getNodeId(mount);
        if(nodeId == null) return null;

        // candidate keys that start with /data/ and contain the node id as a segment
        List<String> candidates = new ArrayList<>();
        for(String key : swagger.getPaths().keySet()) {
            if(!key.startsWith("/data/")) continue;
            // split into segments, ignore leading empty
            String[] segs = key.split("/");
            for(String s : segs) {
                if(s == null || s.isEmpty()) continue;
                // compare with nodeId or with module:nodeId form
                if(s.equals(nodeId) || s.endsWith(":" + nodeId) || s.startsWith(nodeId + "=") || s.contains(":" + nodeId + "=") || s.contains(":" + nodeId)) {
                    candidates.add(key);
                    break;
                }
            }
        }

        if(candidates.isEmpty()) return null;
        // prefer the longest (most specific) path
        candidates.sort(Comparator.comparingInt(String::length).reversed());
        return candidates.get(0);
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
        // do not copy tags - caller should set appropriate tag
        return dst;
    }
}
