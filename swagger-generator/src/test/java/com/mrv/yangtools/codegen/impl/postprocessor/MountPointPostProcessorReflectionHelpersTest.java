package com.mrv.yangtools.codegen.impl.postprocessor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

public class MountPointPostProcessorReflectionHelpersTest {

    private MountPointPostProcessor processor;

    @Before
    public void setUp() throws Exception {
        processor = new MountPointPostProcessor(null, null, null, null);
        Field restricted = MountPointPostProcessor.class.getDeclaredField("declaredAccessRestricted");
        restricted.setAccessible(true);
        restricted.set(null, false);
    }

    @Test
    public void tryGetStringPropertyReturnsFirstMatch() throws Exception {
        Method method = MountPointPostProcessor.class.getDeclaredMethod("tryGetStringProperty", Object.class, String[].class);
        method.setAccessible(true);

        Object value = method.invoke(processor, new PropertySource(), new String[]{"missing", "getName"});

        Assert.assertEquals("expected-name", value);
    }

    @Test
    public void tryGetStringPropertyReturnsNullWhenNoMethodMatches() throws Exception {
        Method method = MountPointPostProcessor.class.getDeclaredMethod("tryGetStringProperty", Object.class, String[].class);
        method.setAccessible(true);

        Object value = method.invoke(processor, new PropertySource(), new String[]{"missingA", "missingB"});

        Assert.assertNull(value);
    }

    @Test
    public void findMountPointLabelReadsArgumentMethod() throws Exception {
        Method method = MountPointPostProcessor.class.getDeclaredMethod("findMountPointLabel", Object.class);
        method.setAccessible(true);

        Object value = method.invoke(processor, new NodeWithEffective(new EffectiveWithCollection(new ItemWithArgument("entry-type-specific-data"))));

        Assert.assertEquals("entry-type-specific-data", value);
    }

    @Test
    public void findMountPointLabelFallsBackToStringParsing() throws Exception {
        Method method = MountPointPostProcessor.class.getDeclaredMethod("findMountPointLabel", Object.class);
        method.setAccessible(true);

        Object value = method.invoke(processor, new NodeWithEffective(new EffectiveWithCollection(new ItemWithText("yangmnt:mount-point parsed-label"))));

        Assert.assertEquals("parsed-label", value);
    }

    @Test
    public void findMountPointLabelReturnsNullWithoutMountPoint() throws Exception {
        Method method = MountPointPostProcessor.class.getDeclaredMethod("findMountPointLabel", Object.class);
        method.setAccessible(true);

        Object value = method.invoke(processor, new NodeWithEffective(new EffectiveWithCollection(new ItemWithText("no-extension"))));

        Assert.assertNull(value);
    }

    @Test
    public void findMountPointLabelReturnsNullForNullNode() throws Exception {
        Method method = MountPointPostProcessor.class.getDeclaredMethod("findMountPointLabel", Object.class);
        method.setAccessible(true);

        Object value = method.invoke(processor, new Object[]{null});

        Assert.assertNull(value);
    }

    private static final class PropertySource {
        public String getName() {
            return "expected-name";
        }
    }

    private static final class NodeWithEffective {
        private final Object effective;

        private NodeWithEffective(Object effective) {
            this.effective = effective;
        }

        public Object asEffectiveStatement() {
            return effective;
        }
    }

    private static final class EffectiveWithCollection {
        private final Object item;

        private EffectiveWithCollection(Object item) {
            this.item = item;
        }

        public java.util.Collection<Object> getItems() {
            return Arrays.asList(item);
        }

        public java.util.Map<String, Object> getMapItems() {
            return Collections.emptyMap();
        }
    }

    private static final class ItemWithArgument {
        private final String label;

        private ItemWithArgument(String label) {
            this.label = label;
        }

        public String getArgument() {
            return label;
        }

        @Override
        public String toString() {
            return "yangmnt:mount-point";
        }
    }

    private static final class ItemWithText {
        private final String text;

        private ItemWithText(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}

