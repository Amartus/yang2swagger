package com.mrv.yangtools.codegen.impl.postprocessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Package-private helper that encapsulates all reflection-based logic for discovering
 * mount-point extension labels from ODL effective-statement objects.
 *
 * <p>ODL's YANG model APIs are partially sealed / non-public, so this class probes
 * the effective-statement tree through a series of fallbacks:</p>
 * <ol>
 *   <li>Resolve the effective statement via {@code asEffectiveStatement()} or
 *       {@code getEffectiveStatement()} (public, then declared).</li>
 *   <li>Collect all Collection/Map/array-typed members from the effective statement
 *       (public methods → declared methods → declared fields).</li>
 *   <li>For each item whose {@code toString()} contains a mount-point token, extract
 *       the label via:
 *       <ul>
 *         <li>Public getter ({@code getArgument}, {@code getName}, …)</li>
 *         <li>Declared getter (same names, with {@code setAccessible})</li>
 *         <li>Regex parse of the toString representation</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Extracting this logic into its own class makes it possible to unit-test the
 * reflection chain in isolation — without standing up a full
 * {@code EffectiveModelContext} — by passing plain stub objects.</p>
 */
class EffectiveStatementReflectionHelper {

    private static final Logger log = LoggerFactory.getLogger(EffectiveStatementReflectionHelper.class);

    static final String[] EFFECTIVE_STATEMENT_METHODS = {"asEffectiveStatement", "getEffectiveStatement"};
    static final String[] ARG_METHODS = {"getArgument", "getArg", "getValue", "getArgumentValue", "getLabel", "getName"};
    static final Pattern MOUNT_POINT_PATTERN = Pattern.compile("mount[-_]point\\s+([a-zA-Z0-9_-]+)");

    private boolean declaredAccessRestricted = false;

    /**
     * Attempts to discover the mount-point extension label for the given schema node
     * by reflectively inspecting its effective statement.
     *
     * @param node a YANG schema node (e.g. {@code DataSchemaNode}, {@code GroupingDefinition})
     * @return the mount-point label, or {@code null} if none found
     */
    String findMountPointLabel(Object node) {
        if (node == null) return null;
        try {
            Object eff = resolveEffectiveStatement(node);
            if (eff == null) return null;

            List<Collection<?>> candidateCols = new ArrayList<>();
            collectCollectionLikeMembersFromMethods(eff, candidateCols, false);
            collectCollectionLikeMembersFromMethods(eff, candidateCols, true);
            collectCollectionLikeMembersFromFields(eff, candidateCols);

            // iterate collected sub-statement collections and look for mount-point tokens
            for (Collection<?> col : candidateCols) {
                if (col == null) continue;
                for (Object item : col) {
                    if (item == null) continue;
                    String text = item.toString().toLowerCase();
                    if (text.contains("mount-point") || text.contains("yangmnt:mount-point") || text.contains("yangmnt:mount_point")) {
                        // try to get argument via public methods on item
                        String arg = tryGetStringProperty(item, ARG_METHODS);
                        if (arg != null && !arg.isEmpty()) return arg;

                        // try declared methods as fallback for non-public item types
                        String declaredArg = tryGetStringPropertyDeclared(item, ARG_METHODS);
                        if (declaredArg != null && !declaredArg.isEmpty()) return declaredArg;

                        // fallback: try to parse token after 'mount-point' in toString
                        int idx = text.indexOf("mount-point");
                        if (idx >= 0) {
                            String after = text.substring(idx);
                            Matcher mm = MOUNT_POINT_PATTERN.matcher(after);
                            if (mm.find()) return mm.group(1);
                        }
                    }
                }
            }
        } catch (java.lang.reflect.InaccessibleObjectException iae) {
            log.trace("Reflective access to effective statement blocked for node class {}: {}", node.getClass(), iae.toString());
            declaredAccessRestricted = true;
            return null;
        } catch (Exception e) {
            log.debug("Error while inspecting node for mount-point: {}", e.toString());
        }
        return null;
    }

    Object resolveEffectiveStatement(Object node) {
        for (String methodName : EFFECTIVE_STATEMENT_METHODS) {
            Object eff = invokePublicNoArg(node, methodName);
            if (eff != null) return eff;
        }
        if (declaredAccessRestricted) return null;
        for (String methodName : EFFECTIVE_STATEMENT_METHODS) {
            Object eff = invokeDeclaredNoArg(node, methodName);
            if (eff != null) return eff;
            if (declaredAccessRestricted) break;
        }
        return null;
    }

    Object invokePublicNoArg(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            log.trace("Cannot invoke public method {} on {}: {}", methodName, target.getClass(), e.toString());
            return null;
        }
    }

    Object invokeDeclaredNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            log.trace("Declared access to {} blocked for {}: {}", methodName, target.getClass(), e.toString());
            declaredAccessRestricted = true;
            return null;
        }
    }

    void collectCollectionLikeMembersFromMethods(Object eff, List<Collection<?>> candidateCols, boolean declared) {
        if (declared && declaredAccessRestricted) return;
        Method[] methods = declared ? eff.getClass().getDeclaredMethods() : eff.getClass().getMethods();
        for (Method method : methods) {
            try {
                Class<?> rt = method.getReturnType();
                if (!(Collection.class.isAssignableFrom(rt) || Map.class.isAssignableFrom(rt) || rt.isArray())) {
                    continue;
                }
                method.setAccessible(true);
                Object res = method.invoke(eff);
                addPossibleCollection(candidateCols, res);
            } catch (IllegalAccessException | InvocationTargetException e) {
                if (declared) {
                    log.trace("Declared method invocation blocked for {}#{}: {}", eff.getClass(), method.getName(), e.toString());
                    declaredAccessRestricted = true;
                    break;
                }
                log.trace("Public method invocation blocked for {}#{}: {}", eff.getClass(), method.getName(), e.toString());
                declaredAccessRestricted = true;
            } catch (Exception e) {
                // ignore this method
            }
        }
    }

    void collectCollectionLikeMembersFromFields(Object eff, List<Collection<?>> candidateCols) {
        if (declaredAccessRestricted) return;
        for (Field f : eff.getClass().getDeclaredFields()) {
            try {
                Class<?> ft = f.getType();
                if (!(Collection.class.isAssignableFrom(ft) || Map.class.isAssignableFrom(ft) || ft.isArray())) {
                    continue;
                }
                f.setAccessible(true);
                Object res = f.get(eff);
                addPossibleCollection(candidateCols, res);
            } catch (IllegalAccessException e) {
                log.trace("Declared field access blocked for {}#{}: {}", eff.getClass(), f.getName(), e.toString());
                declaredAccessRestricted = true;
                break;
            } catch (Exception e) {
                // ignore
            }
        }
    }

    void addPossibleCollection(List<Collection<?>> candidateCols, Object obj) {
        if (obj == null) return;
        if (obj instanceof Collection) {
            candidateCols.add((Collection<?>) obj);
        } else if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Object v : map.values()) {
                if (v instanceof Collection) {
                    candidateCols.add((Collection<?>) v);
                } else {
                    candidateCols.add(Collections.singletonList(v));
                }
            }
        } else if (obj.getClass().isArray()) {
            Object[] arr = (Object[]) obj;
            candidateCols.add(Arrays.asList(arr));
        } else {
            candidateCols.add(Collections.singletonList(obj));
        }
    }

    String tryGetStringProperty(Object obj, String[] candidates) {
        for (String name : candidates) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object v = m.invoke(obj);
                if (v != null) return v.toString();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    String tryGetStringPropertyDeclared(Object obj, String[] candidates) {
        if (declaredAccessRestricted) return null;
        for (String name : candidates) {
            try {
                Method m = obj.getClass().getDeclaredMethod(name);
                m.setAccessible(true);
                Object v = m.invoke(obj);
                if (v != null) return v.toString();
            } catch (IllegalAccessException e) {
                log.trace("Declared access blocked for item method {} of {}: {}", name, obj.getClass(), e.toString());
                declaredAccessRestricted = true;
                return null;
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }
}

