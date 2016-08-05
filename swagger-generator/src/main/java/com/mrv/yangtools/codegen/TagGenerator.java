package com.mrv.yangtools.codegen;

import java.util.Set;

/**
 * @author bartosz.michalik@amartus.com
 */
public interface TagGenerator {
    Set<String> tags(PathSegment segment);
}
