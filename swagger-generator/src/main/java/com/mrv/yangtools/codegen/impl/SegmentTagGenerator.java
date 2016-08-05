package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.PathSegment;
import com.mrv.yangtools.codegen.TagGenerator;
import com.mrv.yangtools.common.BindingMapping;

import java.util.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SegmentTagGenerator implements TagGenerator {

    private final int level;

    public SegmentTagGenerator() {
        this(3);
    }

    public SegmentTagGenerator(int level) {
        this.level = level;
    }

    @Override
    public Set<String> tags(PathSegment segment) {
        Iterator<PathSegment> iterator = segment.iterator();

        LinkedList<String> names = new LinkedList<>();

        while(iterator.hasNext()) {
            names.addFirst(iterator.next().getName());
        }

        String name = names.size() > level ? names.get(level) : names.getLast();

        return Collections.singleton(BindingMapping.getClassName(name));
    }
}
