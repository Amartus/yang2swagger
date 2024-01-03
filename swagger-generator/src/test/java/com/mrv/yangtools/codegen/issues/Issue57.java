/*
 *   Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.issues;

import com.mrv.yangtools.codegen.AbstractItTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Issue57 extends AbstractItTest {
    @Test
    public void testIssue57() {
        swaggerFor(p -> p.getParent().getFileName().toString().equals("bug_57"));
        Set<String> result = swagger.getDefinitions().keySet().stream()
                        .filter(s -> s.endsWith("Input"))
                                .collect(Collectors.toSet());
        Set<String> expected = Stream.of("objects.createobject.Input", "objects.updateobject.Input")
                        .collect(Collectors.toSet());
        Assert.assertEquals(expected, result);
    }
}
