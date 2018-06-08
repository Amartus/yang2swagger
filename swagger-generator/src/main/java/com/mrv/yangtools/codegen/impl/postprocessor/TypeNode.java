package com.mrv.yangtools.codegen.impl.postprocessor;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bartosz.michalik@amartus.com
 */
class TypeNode {
    private final boolean root;
    public final  String type;

    private final Set<TypeNode> using;
    private final Set<TypeNode> usedBy;
    private final Set<TypeNode> referencedBy;
    private final Set<TypeNode> referencing;


    TypeNode(String type) {
        this(type, false);
    }

    TypeNode(String type, boolean root) {
        this.type = type;
        using = new HashSet<>();
        usedBy = new HashSet<>();
        referencedBy = new HashSet<>();
        referencing = new HashSet<>();
        this.root = root;
    }

    boolean isRoot() {
        return root;
    }

    boolean isUsed() {
        return root || usedBy.size() + referencedBy.size() > 0;
    }

    public Set<TypeNode> getUsedBy() {
        return usedBy;
    }

    public Set<TypeNode> getReferencedBy() {
        return referencedBy;
    }

    /**
     * Type node is used by other type node when it is an attribute
     * @param t
     */
    void usedBy(TypeNode t) {
        this.usedBy.add(t);
        t.using.add(this);
    }

    /**
     * Type node is used by other type node in its all-of
     * @param t
     */
    void referencedBy(TypeNode t) {
        this.referencedBy.add(t);
        t.referencing.add(this);
    }


    /**
     * Type nodes  used in all-of
     * @return
     */
    public Set<TypeNode> getReferencing() {
        return referencing;
    }



    public void removingType() {
        using.forEach(u -> u.usedBy.remove(this));
        referencing.forEach(r -> r.referencedBy.remove(this));
    }

    @Override
    public String toString() {
        return type;
    }
}
